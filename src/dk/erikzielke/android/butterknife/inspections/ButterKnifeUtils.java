package dk.erikzielke.android.butterknife.inspections;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;

public class ButterKnifeUtils {
    public static boolean isActivity(PsiClass psiClass) {
        final Project project = psiClass.getProject();
        final GlobalSearchScope searchScope = GlobalSearchScope.allScope(project);
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiClass activityClass = psiFacade.findClass("android.app.Activity", searchScope);
        return activityClass != null && psiClass.isInheritor(activityClass, true);
    }

    public static boolean isView(PsiClass psiClass) {
        final Project project = psiClass.getProject();
        final GlobalSearchScope searchScope = GlobalSearchScope.allScope(project);
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiClass activityClass = psiFacade.findClass("android.view.View", searchScope);
        return activityClass != null && psiClass.isInheritor(activityClass, true);
    }

    public static boolean isFragment(PsiClass psiClass) {
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(psiClass.getProject());
        final GlobalSearchScope searchScope = GlobalSearchScope.allScope(psiClass.getProject());
        PsiClass fragment = psiFacade.findClass("android.app.Fragment", searchScope);
        boolean isFragment;
        if (fragment != null) {
            isFragment = psiClass.isInheritor(fragment, true);
            if (isFragment) return true;
        }

        PsiClass supportFragment = psiFacade.findClass("android.support.v4.app.Fragment", searchScope);
        return supportFragment != null && psiClass.isInheritor(supportFragment, true);
    }
}
