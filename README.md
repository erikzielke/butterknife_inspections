# Butterknife Inspections
This plugin for IntelliJ/Android Studio detects and shows inspections for common mistakes using ButterKnife. For now the problems detected are:
- No call to ButterKnife.inject
- That a field exists in all layouts or is optional
- That the type of the field matches in all views

The detection for which layouts to check for matching ids is based on the following:
- Each setContentView call in onCreate of an activity
- Each inflate in onCreateView in a Fragment
- Each inflate in each constructor in a View
