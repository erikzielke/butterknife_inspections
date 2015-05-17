package dk.erikzielke.android.butterknife.inspections;


import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.slicer.SliceAnalysisParams;
import com.intellij.slicer.SliceUsage;
import com.intellij.util.CommonProcessors;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.android.dom.layout.Include;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.SimpleClassMapConstructor;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ButterKnifeNoViewWithIdInspection extends BaseJavaLocalInspectionTool {
    public static Set<String> butterKnifeAnnotations;

    static {
        butterKnifeAnnotations = new HashSet<String>();
        butterKnifeAnnotations.add("butterknife.InjectView");
        butterKnifeAnnotations.add("butterknife.InjectViews");
        butterKnifeAnnotations.add("butterknife.OnClick");
        butterKnifeAnnotations.add("butterknife.OnCheckedChanged");
        butterKnifeAnnotations.add("butterknife.OnEditorAction");
        butterKnifeAnnotations.add("butterknife.OnFocusChange");
        butterKnifeAnnotations.add("butterknife.OnItemClick");
        butterKnifeAnnotations.add("butterknife.OnLongClick");
        butterKnifeAnnotations.add("butterknife.OnTouch");
        butterKnifeAnnotations.add("butterknife.OnItemSelected");
        butterKnifeAnnotations.add("butterknife.OnPageChange");
        butterKnifeAnnotations.add("butterknife.OnTextChanged");
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new RelatedLayoutFilesVisitor(holder);
    }

    private static class MyJavaElementVisitor extends JavaRecursiveElementWalkingVisitor {

        private PsiClass psiClass;
        private final ProblemsHolder holder;
        private PsiFile layoutFile;

        public MyJavaElementVisitor(PsiClass psiClass, ProblemsHolder holder, PsiFile layoutFile) {
            this.psiClass = psiClass;
            this.holder = holder;
            this.layoutFile = layoutFile;
        }

        @Override
        public void visitClass(PsiClass aClass) {
            if (psiClass.equals(aClass)) {
                super.visitClass(aClass);
            }
        }

        @Override
        public void visitElement(PsiElement element) {
            super.visitElement(element);
        }

        @Override
        public void visitAnnotation(PsiAnnotation annotation) {
            super.visitAnnotation(annotation);
            final String qualifiedName = annotation.getQualifiedName();
            if (qualifiedName != null) {

                if (butterKnifeAnnotations.contains(qualifiedName)) {
                    final PsiElement parent = annotation.getParent();
                    if (parent != null) {
                        final PsiElement psiElement = parent.getParent();
                        if (psiElement != null) {
                            if (psiElement instanceof PsiField || psiElement instanceof PsiMethod) {
                                final PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
                                if (attributes.length > 0) {
                                    final PsiNameValuePair attribute = attributes[0];
                                    final PsiAnnotationMemberValue value = attribute.getValue();
                                    if (value != null) {
                                        if (value instanceof PsiArrayInitializerMemberValue) {
                                            PsiArrayInitializerMemberValue memberValue = (PsiArrayInitializerMemberValue) value;
                                            for (PsiAnnotationMemberValue arrayValue : memberValue.getInitializers()) {
                                                handleValue(annotation, psiElement, arrayValue);
                                            }
                                        } else {
                                            handleValue(annotation, psiElement, value);
                                        }

                                    }
                                }
                            }
                        }
                    }

                }
            }
        }

        private void handleValue(PsiAnnotation annotation, PsiElement psiElement, PsiAnnotationMemberValue value) {
            final PsiReference[] references = value.getReferences();
            for (PsiReference reference : references) {
                final PsiElement element = reference.resolve();
                if (element instanceof PsiField) {
                    final List<PsiElement> resourcesByField = AndroidResourceUtil.findResourcesByField((PsiField) element);
                    PsiElement foundElement = null;
                    boolean found = false;
                    for (PsiElement idElement : resourcesByField) {
                        if (idElement.getContainingFile().equals(layoutFile)) {
                            found = true;
                            foundElement = idElement;
                        } else {
                            boolean isInIncludes = checkIncludes(layoutFile, element);
                            if (isInIncludes) {
                                found = true;
                                foundElement = idElement;
                            }
                        }
                    }
                    if (!found) {
                        if (!isOptional(annotation)) {
                            VirtualFile virtualFile = layoutFile.getVirtualFile();
                            ButterKnifeAddOptional knifeAddOptional = new ButterKnifeAddOptional("butterknife.Optional", (PsiModifierListOwner) psiElement);
                            holder.registerProblem(value, "No Matching id in  " + virtualFile.getParent().getName() + "/" + virtualFile.getPresentableName(), knifeAddOptional);
                        }
                    } else {
                        if (psiElement instanceof PsiField) {
                            PsiField field = (PsiField) psiElement;
                            PsiType psiType = field.getType();
                            AndroidFacet facet = AndroidFacet.getInstance(element);

                            XmlTag tag = null;
                            PsiElement attribute = foundElement.getParent();
                            if (attribute != null) {
                                tag = (XmlTag) attribute.getParent();
                            }

                            if (facet != null && tag != null) {
                                PsiClass tagClass = SimpleClassMapConstructor.findClassByTagName(facet, tag.getName(), "android.view.View");
                                if (tagClass != null) {
                                    PsiClassType classType = PsiTypesUtil.getClassType(tagClass);
                                    if (!psiType.isAssignableFrom(classType)) {
                                        VirtualFile virtualFile = layoutFile.getVirtualFile();
                                        holder.registerProblem(field, "Type is not matching in " + virtualFile.getParent().getName() + "/" + virtualFile.getPresentableName());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        private boolean checkIncludes(PsiFile layoutFile, PsiElement idElement) {
            AndroidFacet facet = AndroidFacet.getInstance(idElement);
            PsiFile containingFile = idElement.getContainingFile();
            DomFileElement<AndroidDomElement> fileElement = DomManager.getDomManager(layoutFile.getProject()).getFileElement((XmlFile) layoutFile, AndroidDomElement.class);
            boolean found = false;

            if (fileElement != null) {
                AndroidDomElement rootElement = fileElement.getRootElement();
                List<Include> includes = DomUtil.getChildrenOfType(rootElement, Include.class);
                for (Include include : includes) {
                    ResourceValue value = include.getLayout().getValue();
                    if (value != null) {
                        if (facet != null) {
                            if (value.getResourceType() != null && value.getResourceName() != null) {
                                List<PsiFile> resourceFiles = facet.getLocalResourceManager().findResourceFiles(value.getResourceType(), value.getResourceName());
                                if (!resourceFiles.isEmpty()) {

                                    found = isInAllIncludes(idElement, found, resourceFiles);
                                }
                            }
                        }
                    }
                }
            }
            return found;
        }

        private boolean isInAllIncludes(PsiElement idElement, boolean found, List<PsiFile> resourceFiles) {
            boolean result = true;
            for (PsiFile resourceFile : resourceFiles) {
                IdsInLayoutXmlVisitor psiElementVisitor = new IdsInLayoutXmlVisitor();
                resourceFile.accept(psiElementVisitor);
                PsiField field = (PsiField) idElement;
                if (!psiElementVisitor.ids.contains(field.getName())) {
                    return false;
                }
            }
            return true;
        }

        private static class IdsInLayoutXmlVisitor extends XmlRecursiveElementVisitor {
            private List<String> ids = new ArrayList<String>();

            @Override
            public void visitElement(PsiElement element) {
                super.visitElement(element);
                if (element instanceof XmlTag) {
                    XmlTag tag = (XmlTag) element;
                    XmlAttribute idAttribute = tag.getAttribute("android:id");
                    if (idAttribute != null) {
                        String value = idAttribute.getValue();
                        if (value != null) {
                            ids.add(value.replace("@+id/", ""));
                        }
                    }
                }
            }
        }
    }

    private static class RelatedLayoutFilesVisitor extends JavaElementVisitor {
        private ProblemsHolder holder;
        private boolean viewHolderHandled = false;

        public RelatedLayoutFilesVisitor(ProblemsHolder holder) {

            this.holder = holder;
        }

        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final AndroidFacet instance = AndroidFacet.getInstance(expression);
            PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);

            if (methodCall != null) {
                PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
                PsiReference reference = methodExpression.getReference();

                if (reference != null) {
                    if (isBaseLayout(expression, reference)) {
                        if (instance != null) {
                            final PsiClass psiClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);

                            findInFiles(expression, instance, psiClass);
                        }
                    } else if (methodCall.getMethodExpression().getCanonicalText().equals("ButterKnife.inject")) {
                        if (!viewHolderHandled) {
                            viewHolderHandled = true;
                            final PsiClass psiClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
                            if (psiClass != null) {
                                if (!ButterKnifeUtils.isActivity(psiClass) && !ButterKnifeUtils.isView(psiClass) && !ButterKnifeUtils.isFragment(psiClass)) {
                                    PsiExpression psiExpression = methodCall.getArgumentList().getExpressions()[1];
                                    if (psiExpression != null) {
                                        PsiReferenceExpression layoutExpression = getFileFromViewHolderInject(psiExpression);
                                        if (layoutExpression != null) {
                                            findInFiles(layoutExpression, instance, psiClass);
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            }
        }

        private void findInFiles(PsiReferenceExpression expression, AndroidFacet instance, PsiClass psiClass) {
            final AndroidResourceUtil.MyReferredResourceFieldInfo info = AndroidResourceUtil.getReferredResourceOrManifestField(instance, expression, false);
            if (info != null && !info.isFromManifest()) {
                final String className = info.getClassName();
                final String fieldName = info.getFieldName();
                final List<PsiElement> related = instance.getLocalResourceManager().findResourcesByFieldName(className, fieldName);
                for (PsiElement psiElement : related) {
                    if (psiElement instanceof PsiFile) {
                        PsiFile layoutFile = (PsiFile) psiElement;


                        if (psiClass != null) {
                            psiClass.accept(new MyJavaElementVisitor(psiClass, holder, layoutFile));
                        }
                    }
                }

            }
        }

        private PsiReferenceExpression getFileFromViewHolderInject(PsiExpression psiExpression) {
            SliceAnalysisParams sliceAnalysisParams = new SliceAnalysisParams();
            sliceAnalysisParams.scope = new AnalysisScope(psiExpression.getProject());
            sliceAnalysisParams.dataFlowToThis = true;
            SliceUsage sliceUsage = SliceUsage.createRootUsage(psiExpression, sliceAnalysisParams);
            ArrayList<SliceUsage> children = new ArrayList<SliceUsage>();
            checkUsages(sliceUsage, children);
            for (SliceUsage child : children) {
                if (child.getElement() instanceof PsiLocalVariable) {
                    PsiLocalVariable localVariable = (PsiLocalVariable) child.getElement();
                    Collection<PsiReference> search = ReferencesSearch.search(localVariable).findAll();
                    for (PsiReference psiReference : search) {
                        if (psiReference instanceof CompositeElement) {
                            CompositeElement treeParent = ((PsiReferenceExpressionImpl) psiReference).getTreeParent();
                            if (treeParent instanceof PsiAssignmentExpression) {
                                PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) treeParent;
                                PsiExpression rExpression = assignmentExpression.getRExpression();
                                if (rExpression instanceof PsiMethodCallExpression) {
                                    PsiMethodCallExpression expression = (PsiMethodCallExpression) rExpression;
                                    PsiReferenceExpression methodExpression = expression.getMethodExpression();
                                    if (methodExpression.getQualifierExpression() != null) {
                                        PsiType type = methodExpression.getQualifierExpression().getType();
                                        if (type != null) {
                                            if (type.getCanonicalText().equals("android.view.LayoutInflater")) {
                                                if ("inflate".equals(methodExpression.getReferenceName())) {
                                                    PsiExpressionList argumentList = expression.getArgumentList();
                                                    PsiExpression layoutFile = argumentList.getExpressions()[0];
                                                    if (layoutFile instanceof PsiReferenceExpression) {
                                                        return (PsiReferenceExpression) layoutFile;
                                                    }
                                                }
                                            }
                                        }
                                    }

                                }
                            }
                        }
                    }
                }
            }
            return null;
        }

        private void checkUsages(SliceUsage sliceUsage, ArrayList<SliceUsage> sliceUsages) {
            List<SliceUsage> usageList = new ArrayList<SliceUsage>();
            sliceUsage.processChildren(new CommonProcessors.CollectProcessor<SliceUsage>(usageList));
            for (SliceUsage child : usageList) {
                checkUsages(child, sliceUsages);
            }
            sliceUsages.addAll(usageList);
        }
    }


    private static boolean isBaseLayout(PsiReferenceExpression expression, PsiReference reference) {
        PsiClass clazz = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
        if (clazz != null) {
            if (ButterKnifeUtils.isActivity(clazz)) {
                String canonicalText = reference.getCanonicalText();
                if (canonicalText.contains("setContentView")) {
                    return true;
                }
            } else if (ButterKnifeUtils.isFragment(clazz)) {
                PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
                if (method != null && method.getName().equals("onCreateView")) {
                    if (fromInflater(reference)) {
                        return true;
                    }
                }
            } else if (ButterKnifeUtils.isView(clazz)) {
                PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
                String clazzName = clazz.getName();
                if (method != null && method.getName().equals(clazzName)) {
                    if (fromInflater(reference)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean fromInflater(PsiReference reference) {
        if (reference instanceof PsiReferenceExpression) {
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) reference;
            PsiElement parent = referenceExpression.getParent();
            if (parent instanceof PsiMethodCallExpression) {
                PsiMethodCallExpressionImpl methodCallExpression = (PsiMethodCallExpressionImpl) ((PsiReferenceExpressionImpl) reference).getParent();
                PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
                PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
                if (qualifierExpression != null) {
                    PsiType psiType = qualifierExpression.getType();
                    if (psiType != null) {
                        String canonicalText = psiType.getCanonicalText();
                        if ("android.view.LayoutInflater".equals(canonicalText)) {
                            if ("inflate".equals(methodExpression.getReferenceName())) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean isOptional(PsiAnnotation psiAnnotation) {
        PsiAnnotationOwner annotationOwner = psiAnnotation.getOwner();
        if (annotationOwner != null) {
            PsiAnnotation annotation = annotationOwner.findAnnotation("butterknife.Optional");
            if (annotation != null)
                return true;
        }
        return false;
    }
}
