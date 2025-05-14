package io.roastedroot.quickjs4j.processor;

import static com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType;
import static com.github.javaparser.StaticJavaParser.parseType;
import static java.lang.String.format;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import io.roastedroot.quickjs4j.annotations.ScriptInterface;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

public class ScriptInterfaceProcessor extends Quickjs4jAbstractProcessor {

    private final Expression processorName = new StringLiteralExpr(getClass().getName());

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(ScriptInterface.class)) {
            log(NOTE, "Generating ScriptInterface for " + element, null);
            try {
                processScriptInterface((TypeElement) element);
            } catch (Quickjs4jAbstractProcessor.AbortProcessingException e) {
                // skip type
            }
        }
        return false;
    }

    private void processScriptInterface(TypeElement type) {
        generateBuiltins(type);
        generateInvokables(type);
        generateProxy(type);
    }

    private void generateInvokables(TypeElement type) {
        var name = type.getSimpleName().toString();
        var excludedMembers = Set.of(type.getAnnotation(ScriptInterface.class).excluded());

        var pkg = getPackageName(type);
        var packageName = pkg.getQualifiedName().toString();
        var cu = (pkg.isUnnamed()) ? new CompilationUnit() : new CompilationUnit(packageName);
        if (!pkg.isUnnamed()) {
            cu.setPackageDeclaration(packageName);
            cu.addImport(type.getQualifiedName().toString());
        }

        cu.addImport("io.roastedroot.quickjs4j.annotations.Invokables");
        cu.addImport("io.roastedroot.quickjs4j.annotations.GuestFunction");

        var clazz =
                cu.addInterface(name + "_Invokables")
                        .addAnnotation("Invokables")
                        .addSingleMemberAnnotation(Generated.class, processorName);

        for (Element member : elements().getAllMembers(type)) {
            if (member.getKind() == ElementKind.METHOD
                    && member instanceof ExecutableElement
                    && !excludedMembers.contains(member.getSimpleName().toString())) {
                var method =
                        clazz.addMethod(member.getSimpleName().toString())
                                .addAnnotation("GuestFunction");
                for (VariableElement parameter : ((ExecutableElement) member).getParameters()) {
                    method.addParameter(
                            parseType(parameter.asType().toString()),
                            parameter.getSimpleName().toString());
                }
                method.setType(parseType(((ExecutableElement) member).getReturnType().toString()));

                List<? extends TypeMirror> thrownTypes =
                        ((ExecutableElement) member).getThrownTypes();
                for (TypeMirror thrownType : thrownTypes) {
                    method.addThrownException(
                            parseType(thrownType.toString()).asClassOrInterfaceType());
                }
                method.removeBody();
            }
        }

        String prefix = (pkg.isUnnamed()) ? "" : packageName + ".";
        String qualifiedName = prefix + type.getSimpleName() + "_Invokables";
        try (Writer writer = filer().createSourceFile(qualifiedName, type).openWriter()) {
            writer.write(cu.printer(printer()).toString());
        } catch (IOException e) {
            log(ERROR, format("Failed to create %s file: %s", qualifiedName, e), null);
        }
    }

    private void generateBuiltins(TypeElement type) {
        var builtinsContext = getContextClassFromAnnotation(getScriptInterfaceAnnotation(type));
        // TODO: test this condition
        if (builtinsContext.getSimpleName().toString().equals("Void")) {
            return;
        }

        var name = type.getSimpleName().toString();
        var excludedMembers = Set.of(type.getAnnotation(ScriptInterface.class).excluded());

        var pkg = getPackageName(type);
        var packageName = pkg.getQualifiedName().toString();
        var cu = (pkg.isUnnamed()) ? new CompilationUnit() : new CompilationUnit(packageName);
        if (!pkg.isUnnamed()) {
            cu.setPackageDeclaration(packageName);
            cu.addImport(type.getQualifiedName().toString());
        }

        cu.addImport("io.roastedroot.quickjs4j.annotations.Builtins");
        cu.addImport("io.roastedroot.quickjs4j.annotations.HostFunction");

        var clazz =
                cu.addClass(name + "_Builtins")
                        .addAnnotation("Builtins")
                        .addSingleMemberAnnotation(Generated.class, processorName);

        clazz.addField(
                parseType(builtinsContext.asType().toString()),
                "delegate",
                Modifier.Keyword.PRIVATE,
                Modifier.Keyword.FINAL);

        var constr = clazz.addConstructor(Modifier.Keyword.PUBLIC);
        constr.addParameter(builtinsContext.asType().toString(), "delegate");
        constr.setBody(
                new BlockStmt()
                        .addStatement(
                                new AssignExpr(
                                        new FieldAccessExpr(new ThisExpr(), "delegate"),
                                        new NameExpr("delegate"),
                                        AssignExpr.Operator.ASSIGN)));

        for (Element member : elements().getAllMembers((TypeElement) builtinsContext)) {
            if (member.getKind() == ElementKind.METHOD
                    && member instanceof ExecutableElement
                    && !excludedMembers.contains(member.getSimpleName().toString())) {
                var method =
                        clazz.addMethod(member.getSimpleName().toString())
                                .addAnnotation("HostFunction");
                var invokeHandle =
                        new MethodCallExpr(
                                new NameExpr("delegate"), member.getSimpleName().toString());
                for (VariableElement parameter : ((ExecutableElement) member).getParameters()) {
                    invokeHandle.addArgument(parameter.getSimpleName().toString());
                    method.addParameter(
                            parseType(parameter.asType().toString()),
                            parameter.getSimpleName().toString());
                }
                method.setType(parseType(((ExecutableElement) member).getReturnType().toString()));

                List<? extends TypeMirror> thrownTypes =
                        ((ExecutableElement) member).getThrownTypes();
                if (thrownTypes != null && thrownTypes.size() > 0) {
                    log(ERROR, "Checked exceptions are not supported in Builtins", type);
                    throw new AbortProcessingException();
                }

                if (extractHasReturn((ExecutableElement) member)) {
                    method.setBody(new BlockStmt().addStatement(new ReturnStmt(invokeHandle)));
                } else {
                    method.setBody(new BlockStmt().addStatement(invokeHandle));
                }
            }
        }

        String prefix = (pkg.isUnnamed()) ? "" : packageName + ".";
        String qualifiedName = prefix + type.getSimpleName() + "_Builtins";
        try (Writer writer = filer().createSourceFile(qualifiedName, type).openWriter()) {
            writer.write(cu.printer(printer()).toString());
        } catch (IOException e) {
            log(ERROR, format("Failed to create %s file: %s", qualifiedName, e), null);
        }
    }

    private void generateProxy(TypeElement type) {
        var builtinsContext = getContextClassFromAnnotation(getScriptInterfaceAnnotation(type));
        var excludedMembers = Set.of(type.getAnnotation(ScriptInterface.class).excluded());

        var name = type.getSimpleName().toString();

        var pkg = getPackageName(type);
        var packageName = pkg.getQualifiedName().toString();
        var cu = (pkg.isUnnamed()) ? new CompilationUnit() : new CompilationUnit(packageName);
        if (!pkg.isUnnamed()) {
            cu.setPackageDeclaration(packageName);
            cu.addImport(type.getQualifiedName().toString());
        }

        cu.addImport("io.roastedroot.quickjs4j.core.Runner");
        cu.addImport("io.roastedroot.quickjs4j.core.Engine");

        var clazz =
                cu.addClass(name + "_Proxy")
                        .addImplementedType(type.getSimpleName().toString())
                        .addImplementedType(AutoCloseable.class)
                        .addSingleMemberAnnotation(Generated.class, processorName);

        clazz.addField("Runner", "runner", Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);
        clazz.addField(
                name + "_Invokables", "delegate", Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);

        var constructor = clazz.addConstructor(Modifier.Keyword.PUBLIC);
        constructor.addParameter("String", "script");
        constructor.addParameter(builtinsContext.asType().toString(), "ctx");
        var constructorBody = new BlockStmt();
        var engineBuilder =
                new MethodCallExpr(
                        new MethodCallExpr(
                                new MethodCallExpr(
                                        new MethodCallExpr(new NameExpr("Engine"), "builder"),
                                        "addBuiltins",
                                        NodeList.nodeList(
                                                new MethodCallExpr(
                                                        new NameExpr(name + "_Builtins_Builtins"),
                                                        "toBuiltins",
                                                        NodeList.nodeList(
                                                                new ObjectCreationExpr(
                                                                        null,
                                                                        parseClassOrInterfaceType(
                                                                                name + "_Builtins"),
                                                                        NodeList.nodeList(
                                                                                new NameExpr(
                                                                                        "ctx"))))))),
                                "addInvokables",
                                NodeList.nodeList(
                                        new MethodCallExpr(
                                                new NameExpr(name + "_Invokables_Invokables"),
                                                "toInvokables"))),
                        "build");

        constructorBody.addStatement(
                new AssignExpr(
                        new VariableDeclarationExpr(parseType("Engine"), "engine"),
                        engineBuilder,
                        AssignExpr.Operator.ASSIGN));
        constructorBody.addStatement(
                new AssignExpr(
                        new FieldAccessExpr(new ThisExpr(), "runner"),
                        new MethodCallExpr(
                                new MethodCallExpr(
                                        new MethodCallExpr(new NameExpr("Runner"), "builder"),
                                        "withEngine",
                                        NodeList.nodeList(new NameExpr("engine"))),
                                "build"),
                        AssignExpr.Operator.ASSIGN));
        constructorBody.addStatement(
                new AssignExpr(
                        new FieldAccessExpr(new ThisExpr(), "delegate"),
                        new MethodCallExpr(
                                new NameExpr(name + "_Invokables_Invokables"),
                                "create",
                                NodeList.nodeList(
                                        new NameExpr("script"),
                                        new FieldAccessExpr(new ThisExpr(), "runner"))),
                        AssignExpr.Operator.ASSIGN));
        constructor.setBody(constructorBody);

        clazz.addMethod("close", Modifier.Keyword.PUBLIC)
                .addAnnotation(Override.class)
                .setBody(
                        new BlockStmt()
                                .addStatement(new MethodCallExpr(new NameExpr("runner"), "close")));

        for (Element member : elements().getAllMembers(type)) {
            if (member.getKind() == ElementKind.METHOD
                    && member instanceof ExecutableElement
                    && !excludedMembers.contains(member.getSimpleName().toString())) {
                var method =
                        clazz.addMethod(member.getSimpleName().toString(), Modifier.Keyword.PUBLIC)
                                .addAnnotation(Override.class);
                var invokeHandle =
                        new MethodCallExpr(
                                new NameExpr("delegate"), member.getSimpleName().toString());
                for (VariableElement parameter : ((ExecutableElement) member).getParameters()) {
                    invokeHandle.addArgument(parameter.getSimpleName().toString());
                    method.addParameter(
                            parseType(parameter.asType().toString()),
                            parameter.getSimpleName().toString());
                }
                method.setType(parseType(((ExecutableElement) member).getReturnType().toString()));

                List<? extends TypeMirror> thrownTypes =
                        ((ExecutableElement) member).getThrownTypes();
                for (TypeMirror thrownType : thrownTypes) {
                    method.addThrownException(
                            parseType(thrownType.toString()).asClassOrInterfaceType());
                }

                if (extractHasReturn((ExecutableElement) member)) {
                    method.setBody(new BlockStmt().addStatement(new ReturnStmt(invokeHandle)));
                } else {
                    method.setBody(new BlockStmt().addStatement(invokeHandle));
                }
            }
        }

        String prefix = (pkg.isUnnamed()) ? "" : packageName + ".";
        String qualifiedName = prefix + type.getSimpleName() + "_Proxy";
        try (Writer writer = filer().createSourceFile(qualifiedName, type).openWriter()) {
            writer.write(cu.printer(printer()).toString());
        } catch (IOException e) {
            log(ERROR, format("Failed to create %s file: %s", qualifiedName, e), null);
        }
    }

    // TODO: ask Eric if the following 2 are standard/safe/known
    private static AnnotationMirror getScriptInterfaceAnnotation(TypeElement element) {
        List<? extends AnnotationMirror> annotationMirrors = element.getAnnotationMirrors();
        for (AnnotationMirror annotationMirror : annotationMirrors) {
            DeclaredType annotationType = annotationMirror.getAnnotationType();
            Element annotationElement = annotationType.asElement();
            String annotationQualifiedName =
                    getPackageName(annotationElement).toString()
                            + "."
                            + annotationElement.getSimpleName();
            if (annotationQualifiedName.equals(ScriptInterface.class.getName())) {
                return annotationMirror;
            }
        }
        return null;
    }

    private static Element getContextClassFromAnnotation(
            AnnotationMirror scriptInterfaceAnnotation) {
        Map<? extends ExecutableElement, ? extends AnnotationValue> values =
                scriptInterfaceAnnotation.getElementValues();
        Set<? extends Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>> entries =
                values.entrySet();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : entries) {
            String name = entry.getKey().getSimpleName().toString();
            if (name.equals("context")) {
                AnnotationValue value = entry.getValue();
                TypeMirror typeMirror = (TypeMirror) value.getValue();
                if (typeMirror.getKind() == TypeKind.DECLARED) {
                    DeclaredType declaredType = (DeclaredType) typeMirror;
                    TypeElement typeElement = (TypeElement) declaredType.asElement();
                    return typeElement;
                }
            }
        }
        return null;
    }
}
