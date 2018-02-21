package com.footprint.viewgenerator.common;

import com.footprint.viewgenerator.Settings.Settings;
import com.footprint.viewgenerator.model.Element;
import com.footprint.viewgenerator.model.VGContext;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.awt.RelativePoint;
import org.apache.http.util.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    //TODO SonVo Begin
    private static final String XML_FILE_SUFFIX = ".xml";
    public static final String LAYOUT_RES_SUFFIX = "R.layout.";
    public static final String ANDROID_APP_ACTIVITY_FQ_PATH = "android.app.Activity";
    public static final String ANDROID_APP_FRAGMENT_FQ_PATH = "android.app.Fragment";

    public static final String METHOD_PARAMS_DELIMITER = ",";
    public static final String METHOD_NAME_ON_CREATE = "onCreate";
    public static final String METHOD_NAME_SET_CONTENT_VIEW = "setContentView";
    public static final String METHOD_NAME_ON_CREATE_VIEW = "onCreateView";
    public static final String METHOD_NAME_ON_CREATE_VIEW_HOLDER = "onCreateViewHolder";
    public static final String ANDROID_SUPPORT_V4_APP_FRAGMENT_FQ_PATH = "android.support.v4.app.Fragment";
    //TODO SonVo End
    /**
     * Is using Android SDK?
     */
    public static Sdk findAndroidSDK() {
        Sdk[] allJDKs = ProjectJdkTable.getInstance().getAllJdks();
        for (Sdk sdk : allJDKs) {
            if (sdk.getSdkType().getName().toLowerCase().contains("android")) {
                return sdk;
            }
        }

        return null; // no Android SDK found
    }

    /**
     * Try to find layout XML file in current source on cursor's position
     *
     * @param editor
     * @param file
     * @return
     */
    public static PsiFile getLayoutFileFromCaret(Editor editor, PsiFile file) {
        int offset = editor.getCaretModel().getOffset();

        PsiElement candidateA = file.findElementAt(offset);
        PsiElement candidateB = file.findElementAt(offset - 1);

        PsiFile layout = findLayoutResource(candidateA);
        if (layout != null) {
            return layout;
        }

        return findLayoutResource(candidateB);
    }

    /**
     * Try to find layout XML file in selected element
     *
     * @param element
     * @return
     */
    public static PsiFile findLayoutResource(PsiElement element) {
        if (element == null) {
            return null; // nothing to be used
        }
        if (!(element instanceof PsiIdentifier)) {
            return null; // nothing to be used
        }

        PsiElement layout = element.getParent().getFirstChild();
        if (layout == null) {
            return null; // no file to process
        }
        if (!"R.layout".equals(layout.getText())) {
            return null; // not layout file
        }

        Project project = element.getProject();
        String name = String.format("%s.xml", element.getText());
        return resolveLayoutResourceFile(element, project, name);


    }

    private static PsiFile resolveLayoutResourceFile(PsiElement element, Project project, String name) {
        // restricting the search to the current module - searching the whole project could return wrong layouts
        Module module = ModuleUtil.findModuleForPsiElement(element);
        PsiFile[] files = null;
        if (module != null) {
            GlobalSearchScope moduleScope = module.getModuleWithDependenciesAndLibrariesScope(false);
            files = FilenameIndex.getFilesByName(project, name, moduleScope);
        }
        if (files == null || files.length <= 0) {
            // fallback to search through the whole project
            // useful when the project is not properly configured - when the resource directory is not configured
            files = FilenameIndex.getFilesByName(project, name, new EverythingGlobalScope(project));
            if (files.length <= 0) {
                return null; //no matching files
            }
        }

        // TODO - we have a problem here - we still can have multiple layouts (some coming from a dependency)
        // we need to resolve R class properly and find the proper layout for the R class
        return files[0];
    }

    /**
     * Try to find layout XML file by name
     *
     * @param file
     * @param project
     * @param fileName
     * @return
     */
    public static PsiFile findLayoutResource(PsiFile file, Project project, String fileName) {
        String name = String.format("%s.xml", fileName);
        // restricting the search to the module of layout that includes the layout we are seaching for
        return resolveLayoutResourceFile(file, project, name);
    }

    /**
     * Obtain all IDs from layout
     *
     * @param file
     * @return
     */
    public static ArrayList<Element> getIDsFromLayout(final PsiFile file) {
        final ArrayList<Element> elements = new ArrayList<Element>();

        return getIDsFromLayout(file, elements);
    }

    /**
     * Obtain all IDs from layout
     *
     * @param file
     * @return
     */
    public static ArrayList<Element> getIDsFromLayout(final PsiFile file, final ArrayList<Element> elements) {
        file.accept(new XmlRecursiveElementVisitor() {

            @Override
            public void visitElement(final PsiElement element) {
                super.visitElement(element);

                if (element instanceof XmlTag) {
                    XmlTag tag = (XmlTag) element;

                    if (tag.getName().equalsIgnoreCase("include")) {
                        XmlAttribute layout = tag.getAttribute("layout", null);

                        if (layout != null) {
                            Project project = file.getProject();
                            PsiFile include = findLayoutResource(file, project, getLayoutName(layout.getValue()));

                            if (include != null) {
                                getIDsFromLayout(include, elements);

                                return;
                            }
                        }
                    }

                    // get element ID
                    XmlAttribute id = tag.getAttribute("android:id", null);
                    if (id == null) {
                        return; // missing android:id attribute
                    }
                    String value = id.getValue();
                    if (value == null) {
                        return; // empty value
                    }

                    // check if there is defined custom class
                    String name = tag.getName();
                    XmlAttribute clazz = tag.getAttribute("class", null);
                    if (clazz != null) {
                        name = clazz.getValue();
                    }

                    try {
                        elements.add(new Element(name, value));
                    } catch (IllegalArgumentException e) {
                        // TODO log
                    }
                }
            }
        });

        return elements;
    }

    /**
     * Get layout name from XML identifier (@layout/....)
     *
     * @param layout
     * @return
     */
    public static String getLayoutName(String layout) {
        if (layout == null || !layout.startsWith("@") || !layout.contains("/")) {
            return null; // it's not layout identifier
        }

        String[] parts = layout.split("/");
        if (parts.length != 2) {
            return null; // not enough parts
        }

        return parts[1];
    }

    /**
     * Display simple notification - information
     *
     * @param project
     * @param text
     */
    public static void showInfoNotification(Project project, String text) {
        showNotification(project, MessageType.INFO, text);
    }

    /**
     * Display simple notification - error
     *
     * @param project
     * @param text
     */
    public static void showErrorNotification(Project project, String text) {
        showNotification(project, MessageType.ERROR, text);
    }

    /**
     * Display simple notification of given type
     *
     * @param project
     * @param type
     * @param text
     */
    public static void showNotification(Project project, MessageType type, String text) {
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);

        JBPopupFactory.getInstance()
                .createHtmlTextBalloonBuilder(text, type, null)
                .setFadeoutTime(7500)
                .createBalloon()
                .show(RelativePoint.getCenterOf(statusBar.getComponent()), Balloon.Position.atRight);
    }

    /**
     * Load field name prefix from code style
     *
     * @return
     */
    public static String getPrefix() {
        if (PropertiesComponent.getInstance().isValueSet(Settings.PREFIX)) {
            return PropertiesComponent.getInstance().getValue(Settings.PREFIX);
        } else {
            CodeStyleSettingsManager manager = CodeStyleSettingsManager.getInstance();
            CodeStyleSettings settings = manager.getCurrentSettings();
            return settings.FIELD_NAME_PREFIX;
        }
    }

    public static String getViewHolderClassName() {
        return PropertiesComponent.getInstance().getValue(Settings.VIEWHOLDER_CLASS_NAME, "ViewHolder");
    }

    /**
     * 删除不需要处理的Element
     */
    public static void dealElementList(ArrayList<Element> elements) {
        Iterator<Element> iterator = elements.iterator();
        while (iterator.hasNext()) {
            Element element = iterator.next();
            if (!element.needDeal)
                iterator.remove();
        }
    }

    public static int getInjectCount(VGContext context, ArrayList<Element> elements) {
        int cnt = 0;
        for (Element element : elements) {
            if (!context.getFieldNameList().contains(element.fieldName)) {
                cnt++;
            }
        }
        return cnt;
    }

    public static int getClickCount(VGContext context, ArrayList<Element> elements) {
        int cnt = 0;
        for (Element element : elements) {
            if (!context.getClickIdsList().contains(element.getFullID())
                    && element.isClick) {
                cnt++;
            }
        }
        return cnt;
    }

    /**
     * Easier way to check if string is empty
     *
     * @param text
     * @return
     */
    public static boolean isEmptyString(String text) {
        return (text == null || text.trim().length() == 0);
    }

    /**
     * Check whether classpath of a module that corresponds to a {@link PsiElement} contains given class.
     *
     * @param project    Project
     * @param psiElement Element for which we check the class
     * @param className  Class name of the searched class
     * @return True if the class is present on the classpath
     * @since 1.3
     */
    public static boolean isClassAvailableForPsiFile(@NotNull Project project, @NotNull PsiElement psiElement, @NotNull String className) {
        Module module = ModuleUtil.findModuleForPsiElement(psiElement);
        if (module == null) {
            return false;
        }
        GlobalSearchScope moduleScope = module.getModuleWithDependenciesAndLibrariesScope(false);
        PsiClass classInModule = JavaPsiFacade.getInstance(project).findClass(className, moduleScope);
        return classInModule != null;
    }

    /**
     * Check whether classpath of a the whole project contains given class.
     * This is only fallback for wrongly setup projects.
     *
     * @param project   Project
     * @param className Class name of the searched class
     * @return True if the class is present on the classpath
     * @since 1.3.1
     */
    public static boolean isClassAvailableForProject(@NotNull Project project, @NotNull String className) {
        PsiClass classInModule = JavaPsiFacade.getInstance(project).findClass(className,
                new EverythingGlobalScope(project));
        return classInModule != null;
    }

    /**
     * 去除空格换行等字符
     */
    public static String replaceBlank(String str) {
        String dest = "";
        if (str != null) {
            Pattern p = Pattern.compile("\\s*|\t|\r|\n");
            Matcher m = p.matcher(str);
            dest = m.replaceAll("");
        }
        return dest;
    }

    public static boolean isLayoutStatement(PsiStatement statement) {
        return statement instanceof PsiExpressionStatement && statement.getText().contains("R.layout.");
    }

    public static String getIdFromLayoutStatement(String layoutStatement) {
        StringBuilder stringBuilder = new StringBuilder(layoutStatement);
        stringBuilder.delete(0, stringBuilder.indexOf("R.layout."));

        if (stringBuilder.indexOf(")") > 0) {
            stringBuilder.delete(stringBuilder.indexOf(")"), stringBuilder.length());
        }

        if (stringBuilder.indexOf(";") > 0) {
            stringBuilder.delete(stringBuilder.indexOf(";"), stringBuilder.length());
        }

        return replaceBlank(stringBuilder.toString());
    }

    public static void clearStringBuilder(StringBuilder builder) {
        if (builder == null)
            return;

        builder.delete(0, builder.length());
    }

    public static boolean ifClassContainsMethod(PsiClass psiClass, String methodName) {
        return psiClass.findMethodsByName(methodName, false).length != 0;
    }

    //TODO SonVo
    @NotNull
    public static String tryGetLayoutFileNameAutomatically(@NotNull final Editor editor) {
        PsiClass psiClass = getPsiClassInEditor(editor);
        if (psiClass == null) {
            return "";
        }

        String layoutFileName = tryGetLayoutFileNameBySelectedText(editor);
        if (!layoutFileName.isEmpty()) {
            return layoutFileName;
        }

        layoutFileName = tryGetLayoutFileNameInCaretLine(editor);
        if (!layoutFileName.isEmpty()) {
            return layoutFileName;
        }

        layoutFileName = getLayoutFileNameInActivity(psiClass);
        if (!layoutFileName.isEmpty()) {
            return layoutFileName;
        }


        layoutFileName = getLayoutFileNameInFragment(psiClass);
        if (!layoutFileName.isEmpty()) {
            return layoutFileName;
        }

        layoutFileName = getLayoutFileNameInRecyclerViewAdapter(psiClass);
        if (!layoutFileName.isEmpty()) {
            return layoutFileName;
        }


        return layoutFileName;
    }

    //TODO SonVo
    @NotNull
    private static String getLayoutFileNameInRecyclerViewAdapter(@NotNull final PsiClass psiClass) {
        PsiCodeBlock onCreateViewHolderMethodBody = getSpecifiedMethodBody(psiClass, METHOD_NAME_ON_CREATE_VIEW_HOLDER);
        if (onCreateViewHolderMethodBody == null) {
            return "";
        }

        String layoutFileName = "";

        PsiStatement[] onCreateViewHolderMethodBodyStatements = onCreateViewHolderMethodBody.getStatements();
        for (PsiStatement statement : onCreateViewHolderMethodBodyStatements) {
            if (!layoutFileName.isEmpty()) {
                break;
            }
            String statementText = statement.getText();
            layoutFileName = findLayoutFileNameInText(statementText);
        }

        return layoutFileName;
    }

    //TODO SonVo
    private static String getLayoutFileNameInFragment(@NotNull final PsiClass psiClass) {
        if (!isFragmentClass(psiClass)) {
            return "";
        }

        PsiCodeBlock onCreateViewMethodBody = getSpecifiedMethodBody(psiClass, METHOD_NAME_ON_CREATE_VIEW);
        if (onCreateViewMethodBody == null) {
            return "";
        }

        PsiStatement[] onCreateViewMethodBodyStatements = onCreateViewMethodBody.getStatements();

        String layoutFileName = "";

        for (PsiStatement statement : onCreateViewMethodBodyStatements) {
            if (!layoutFileName.isEmpty()) {
                break;
            }
            String statementText = statement.getText();
            layoutFileName = findLayoutFileNameInText(statementText);
        }

        return layoutFileName;
    }

    //TODO SonVo
    /**
     * 判断指定 class 是不是直接或间接继承 android.app.Fragment 或者 android.support.v4.app.Fragment 类
     *
     * @param psiClass 指定的 class
     * @return <code>true</code> means yes, <code>false</code> means no
     */
    public static boolean isFragmentClass(@NotNull final PsiClass psiClass) {
        Project project = psiClass.getProject();
        EverythingGlobalScope globalScope = new EverythingGlobalScope(project);
        PsiClass fragmentClass = JavaPsiFacade.getInstance(project).findClass(ANDROID_APP_FRAGMENT_FQ_PATH, globalScope);
        PsiClass fragmentV4Class = JavaPsiFacade.getInstance(project).findClass(ANDROID_SUPPORT_V4_APP_FRAGMENT_FQ_PATH, globalScope);
        return (fragmentClass != null && psiClass.isInheritor(fragmentClass, true))
                || (fragmentV4Class != null && psiClass.isInheritor(fragmentV4Class, true));
    }

    //TODO SonVo
    /**
     * 如果当前类是一个 Activity 类，则在其 onCreate 方法的 setContentView 方法调用语句中获取当前 Activity 的布局资源文件名.
     * 不考虑其他的一些在其他地方定义布局文件资源的类封装
     *
     * @param psiClass 指定的类
     * @return 当前 Activity 的布局资源文件名
     */
    @NotNull
    private static String getLayoutFileNameInActivity(@NotNull PsiClass psiClass) {
        if (!isActivityClass(psiClass)) {
            return "";
        }

        PsiCodeBlock onCreateMethodBody = getSpecifiedMethodBody(psiClass, METHOD_NAME_ON_CREATE);
        if (onCreateMethodBody == null) {
            return "";
        }

        String layoutFileName = "";

        for (PsiStatement psiStatement : onCreateMethodBody.getStatements()) {
            if (!layoutFileName.isEmpty()) {
                break;
            }
            // 查找setContentView
            PsiElement psiElement = psiStatement.getFirstChild();
            if (psiElement instanceof PsiMethodCallExpression) {
                PsiReferenceExpression methodExpression = ((PsiMethodCallExpression) psiElement).getMethodExpression();
                if (methodExpression.getText().equals(METHOD_NAME_SET_CONTENT_VIEW)) {
                    String[] methodCallParams = extractParamsFromMethodCall((PsiMethodCallExpression) psiElement);
                    if (methodCallParams != null && methodCallParams.length > 0) {
                        String fullLayoutFilePath = methodCallParams[0];
                        layoutFileName = fullLayoutFilePath.replace(LAYOUT_RES_SUFFIX, "");
                        break;
                    }
                }
            }
        }

        return layoutFileName;
    }

    //TODO SonVo
    @Nullable
    public static String[] extractParamsFromMethodCall(@NotNull PsiMethodCallExpression methodCallExpression) {
        String methodCallExpressionText = StringUtils.removeBlanksInString(methodCallExpression.getText());
        List<String> stringList = StringUtils.extractStringInParentheses(methodCallExpressionText);
        if (stringList.isEmpty()) {
            return null;
        }

        return stringList.get(0).split(METHOD_PARAMS_DELIMITER);
    }

    //TODO SonVo
    @Nullable
    public static PsiCodeBlock getSpecifiedMethodBody(@NotNull final PsiClass psiClass, @NotNull final String methodName) {
        PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
        if (methods.length < 1) {
            return null;
        }

        return methods[0].getBody();
    }

    //TODO SonVo
    /**
     * 判断当前打开的类文件是否是一个 Activity class
     * 通过判断是 android.app.Activity 类的直接子类或间接子类来确定，
     * 如继承了 AppCompatActivity, FragmentActivity 都是 Activity 的子类，因为 AppCompatActivity 等是 Activity 的间接子类.
     *
     * @param psiClass current opened & displayed file's class
     * @return <code>true</code> means yes, <code>false</code> means no
     */
    public static boolean isActivityClass(@NotNull final PsiClass psiClass) {
        Project project = psiClass.getProject();
        EverythingGlobalScope globalScope = new EverythingGlobalScope(project);
        PsiClass activityClass = JavaPsiFacade.getInstance(project).findClass(ANDROID_APP_ACTIVITY_FQ_PATH, globalScope);
        return (activityClass != null && psiClass.isInheritor(activityClass, true));
    }

    //TODO SonVo
    @NotNull
    private static String tryGetLayoutFileNameInCaretLine(@NotNull final Editor editor) {
        String currentLineText = getCaretLineText(editor);
        return findLayoutFileNameInText(currentLineText);
    }

    //TODO SonVo
    @NotNull
    private static String findLayoutFileNameInText(@NotNull final String srcText) {
        String srcTextWithoutBlanks = StringUtils.removeBlanksInString(srcText);
        int index = srcTextWithoutBlanks.indexOf(LAYOUT_RES_SUFFIX);
        if (index < 0) {
            return "";
        }

        StringBuilder layoutFileNameBuilder = new StringBuilder("");
        char[] chars = srcTextWithoutBlanks.substring(index + LAYOUT_RES_SUFFIX.length()).toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == ',' || chars[i] == ')') {
                break;
            }
            layoutFileNameBuilder.append(chars[i]);
        }

        return layoutFileNameBuilder.toString();
    }

    //TODO SonVo
    /**
     * 获取当前光标所在行的字符串（包含前导和末尾的所有字符）
     * @param editor 当前编辑器
     * @return 当前光标所在行的文本
     */
    @NotNull
    public static String getCaretLineText(@NotNull Editor editor) {
        Document document = editor.getDocument();
        CaretModel caretModel = editor.getCaretModel();
        int caretModelOffset = caretModel.getOffset();

        int lineNumber = document.getLineNumber(caretModelOffset);
        int lineStartOffset = document.getLineStartOffset(lineNumber);
        int lineEndOffset = document.getLineEndOffset(lineNumber);

        return document.getText(new TextRange(lineStartOffset, lineEndOffset));
    }

    //TODO SonVo
    /**
     * 根据当前文件获取其对应的class
     *
     * @param editor 当前项目编辑器
     * @return 当前编辑器内的文件的类
     */
    @Nullable
    public static PsiClass getPsiClassInEditor(@NotNull final Editor editor) {
        PsiElement element = PsiUtilBase.getElementAtCaret(editor);
        if (element == null) {
            return null;
        } else {
            PsiClass target = PsiTreeUtil.getParentOfType(element, PsiClass.class);
            return target instanceof SyntheticElement ? null : target;
        }
    }

    //TODO SonVo
    @NotNull
    private static String tryGetLayoutFileNameBySelectedText(@NotNull final Editor editor) {
        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();
        if (TextUtils.isBlank(selectedText)) {
            return "";
        } else {
            Project project = editor.getProject();
            if (project == null) {
                return "";
            }
            String layoutFileName = StringUtils.removeBlanksInString(selectedText);

            VirtualFile currentFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
            if (currentFile == null) {
                return "";
            }

            XmlFile xmlFile;
            Module module = ModuleUtil.findModuleForFile(currentFile, project);
            if (module == null) {
                xmlFile = getXmlFileByName(project, layoutFileName);
            } else {
                xmlFile = getXmlFileByNameInModule(module, layoutFileName);
            }
            if (xmlFile != null) {
                return layoutFileName;
            } else {
                return "";
            }
        }
    }

    //TODO SonVo
    @Nullable
    public static XmlFile getXmlFileByNameInModule(@NotNull Module module, @NotNull String fileName) {
        String xmlFileName = fileName + XML_FILE_SUFFIX;
        return (XmlFile) getFileByNameInModule(module, xmlFileName);
    }

    //TODO SonVo
    @Nullable
    public static XmlFile getXmlFileByName(@NotNull Project project, @NotNull String fileName) {
        String xmlFileName = fileName + XML_FILE_SUFFIX;
        return (XmlFile) getFileByName(project, xmlFileName);
    }

    //TODO SonVo
    @Nullable
    public static PsiFile getFileByNameInModule(@NotNull final Module module, @NotNull final String fileName) {
        Project project = module.getProject();
        return getFileByNameWithinSearchScope(project, fileName, GlobalSearchScope.moduleScope(module));
    }

    //TODO SonVo
    @Nullable
    public static PsiFile getFileByName(@NotNull Project project, @NotNull String fileName) {
        return getFileByNameWithinSearchScope(project, fileName, GlobalSearchScope.allScope(project));
    }

    //TODO SonVo
    @Nullable
    public static PsiFile getFileByNameWithinSearchScope(@NotNull final Project project, @NotNull final String fileName,
                                                         @NotNull final GlobalSearchScope searchScope) {
        PsiFile[] filesByName = FilenameIndex.getFilesByName(project, fileName, searchScope);
        if (filesByName.length > 0) {
            return filesByName[0];
        } else {
            return null;
        }
    }

    //TODO SonVo
    @NotNull
    public static String removeBlanksInString(@NotNull final String s) {
        return s.replace(" ", "");
    }

}
