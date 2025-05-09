package io.roastedroot.quickjs4j.processor;

import static com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType;
import static com.github.javaparser.StaticJavaParser.parseType;
import static com.github.javaparser.printer.configuration.DefaultPrinterConfiguration.ConfigOption.COLUMN_ALIGN_PARAMETERS;
import static java.lang.String.format;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;

import com.github.javaparser.ast.ArrayCreationLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import io.roastedroot.quickjs4j.annotations.Builtins;
import io.roastedroot.quickjs4j.annotations.GuestFunction;
import io.roastedroot.quickjs4j.annotations.HostFunction;
import io.roastedroot.quickjs4j.annotations.HostRefParam;
import io.roastedroot.quickjs4j.annotations.Invokables;
import io.roastedroot.quickjs4j.annotations.ReturnsHostRef;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Generated;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

public final class JsModuleProcessor extends AbstractProcessor {
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    Elements elements() {
        return processingEnv.getElementUtils();
    }

    Filer filer() {
        return processingEnv.getFiler();
    }

    void log(Diagnostic.Kind kind, String message, Element element) {
        processingEnv.getMessager().printMessage(kind, message, element);
    }

    static PackageElement getPackageName(Element element) {
        Element enclosing = element;
        while (enclosing.getKind() != ElementKind.PACKAGE) {
            enclosing = enclosing.getEnclosingElement();
        }
        return (PackageElement) enclosing;
    }

    static DefaultPrettyPrinter printer() {
        return new DefaultPrettyPrinter(
                new DefaultPrinterConfiguration()
                        .addOption(new DefaultConfigurationOption(COLUMN_ALIGN_PARAMETERS, true)));
    }

    static final class AbortProcessingException extends RuntimeException {}

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Builtins.class.getName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Builtins.class)) {
            log(NOTE, "Generating Builtins for " + element, null);
            try {
                processBuiltins((TypeElement) element);
            } catch (AbortProcessingException e) {
                // skip type
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(Invokables.class)) {
            log(NOTE, "Generating Invokables for " + element, null);
            try {
                processInvokables((TypeElement) element);
            } catch (AbortProcessingException e) {
                // skip type
            }
        }

        return false;
    }

    private void processInvokables(TypeElement type) {
        var moduleName = type.getAnnotation(Invokables.class).value();
        if (moduleName.isEmpty()) {
            moduleName = type.getSimpleName().toString();
        }

        var pkg = getPackageName(type);
        var packageName = pkg.getQualifiedName().toString();
        var cu = (pkg.isUnnamed()) ? new CompilationUnit() : new CompilationUnit(packageName);
        if (!pkg.isUnnamed()) {
            cu.setPackageDeclaration(packageName);
            cu.addImport(type.getQualifiedName().toString());
        }

        cu.addImport("io.roastedroot.quickjs4j.core.Runner");
        cu.addImport("io.roastedroot.quickjs4j.core.Invokables");
        cu.addImport("io.roastedroot.quickjs4j.core.GuestFunction");
        // TODO: verify HostRefs in GuestFunctions
        cu.addImport("io.roastedroot.quickjs4j.core.HostRef");
        cu.addImport(List.class);

        var typeName = type.getSimpleName().toString();
        var processorName = new StringLiteralExpr(getClass().getName());
        var className = typeName + "_Invokables";
        var classDef =
                cu.addClass(className)
                        .setPublic(true)
                        .setFinal(true)
                        .addImplementedType(typeName)
                        .addSingleMemberAnnotation(Generated.class, processorName);

        classDef.addField(String.class, "jsLibrary", Modifier.Keyword.FINAL);
        classDef.addField("io.roastedroot.quickjs4j.core.Runner", "runner", Modifier.Keyword.FINAL);

        var constructor =
                classDef.addConstructor()
                        .addParameter(String.class, "jsLibrary")
                        .addParameter("io.roastedroot.quickjs4j.core.Runner", "runner")
                        .setPrivate(true);

        constructor
                .createBody()
                .addStatement(
                        new AssignExpr(
                                new FieldAccessExpr(new ThisExpr(), "jsLibrary"),
                                new NameExpr("jsLibrary"),
                                AssignExpr.Operator.ASSIGN))
                .addStatement(
                        new AssignExpr(
                                new FieldAccessExpr(new ThisExpr(), "runner"),
                                new NameExpr("runner"),
                                AssignExpr.Operator.ASSIGN));

        List<Expression> functions = new ArrayList<>();
        for (Element member : elements().getAllMembers(type)) {
            if (member instanceof ExecutableElement && annotatedWith(member, GuestFunction.class)) {
                var name = member.getAnnotation(GuestFunction.class).value();
                if (name.isEmpty()) {
                    name = member.getSimpleName().toString();
                }

                var executable = (ExecutableElement) member;

                var overriddenMethod =
                        classDef.addMethod(
                                        member.getSimpleName().toString(), Modifier.Keyword.PUBLIC)
                                .addAnnotation(Override.class);

                NodeList<Expression> arguments = NodeList.nodeList();
                for (int i = 0; i < executable.getParameters().size(); i++) {
                    overriddenMethod.addParameter(
                            executable.getParameters().get(i).asType().toString(), "arg" + i);
                    arguments.add(new NameExpr("arg" + i));
                }
                var argsList =
                        new MethodCallExpr(new NameExpr("List"), new SimpleName("of"), arguments);

                var methodBody = overriddenMethod.createBody();

                var invocationHandle =
                        new MethodCallExpr(
                                new NameExpr("runner"),
                                new SimpleName("invokeGuestFunction"),
                                NodeList.nodeList(
                                        new StringLiteralExpr(moduleName),
                                        new StringLiteralExpr(name),
                                        argsList,
                                        new NameExpr("jsLibrary")));

                var hasReturn = extractHasReturn(executable);
                if (hasReturn) {
                    var returnType = parseType(executable.getReturnType().toString());
                    overriddenMethod.setType(returnType);
                    methodBody.addStatement(
                            new ReturnStmt(new CastExpr(returnType, invocationHandle)));
                } else {
                    methodBody.addStatement(invocationHandle);
                    methodBody.addStatement(new ReturnStmt(new NullLiteralExpr()));
                }

                functions.add(processGuestFunction((ExecutableElement) member));
            }
        }

        var newJsFunctions =
                new ArrayCreationExpr(
                        parseType("GuestFunction"),
                        new NodeList<>(new ArrayCreationLevel()),
                        new ArrayInitializerExpr(NodeList.nodeList(functions)));

        var invokablesCreationHandle =
                new MethodCallExpr(
                        new MethodCallExpr(
                                new MethodCallExpr(
                                        new NameExpr("Invokables"),
                                        new SimpleName("builder"),
                                        NodeList.nodeList(new StringLiteralExpr(moduleName))),
                                new SimpleName("add"),
                                NodeList.nodeList(newJsFunctions)),
                        new SimpleName("build"),
                        NodeList.nodeList());

        classDef.addMethod("toInvokables")
                .setPublic(true)
                .setStatic(true)
                .setType("Invokables")
                .setBody(new BlockStmt(new NodeList<>(new ReturnStmt(invokablesCreationHandle))));

        classDef.addMethod("create")
                .setPublic(true)
                .setStatic(true)
                .addParameter(String.class, "jsLibrary")
                .addParameter("io.roastedroot.quickjs4j.core.Runner", "runner")
                .setType(typeName)
                .setBody(
                        new BlockStmt(
                                new NodeList<>(
                                        new ReturnStmt(
                                                new ObjectCreationExpr(
                                                        null,
                                                        parseClassOrInterfaceType(className),
                                                        NodeList.nodeList(
                                                                new NameExpr("jsLibrary"),
                                                                new NameExpr("runner")))))));

        String prefix = (pkg.isUnnamed()) ? "" : packageName + ".";
        String qualifiedName = prefix + type.getSimpleName() + "_Invokables";
        try (Writer writer = filer().createSourceFile(qualifiedName, type).openWriter()) {
            writer.write(cu.printer(printer()).toString());
        } catch (IOException e) {
            log(ERROR, format("Failed to create %s file: %s", qualifiedName, e), null);
        }
    }

    private void processBuiltins(TypeElement type) {
        var name = type.getAnnotation(Builtins.class).value();
        if (name.isEmpty()) {
            name = type.getSimpleName().toString();
        }

        StringBuilder mjsBuilder = new StringBuilder();
        mjsBuilder.append("// File generated by QuickJs4j\n\n");

        List<Expression> functions = new ArrayList<>();
        for (Element member : elements().getAllMembers(type)) {
            if (member instanceof ExecutableElement && annotatedWith(member, HostFunction.class)) {
                functions.add(processHostFunction((ExecutableElement) member, name, mjsBuilder));
            }
        }

        var pkg = getPackageName(type);
        var packageName = pkg.getQualifiedName().toString();
        var cu = (pkg.isUnnamed()) ? new CompilationUnit() : new CompilationUnit(packageName);
        if (!pkg.isUnnamed()) {
            cu.setPackageDeclaration(packageName);
            cu.addImport(type.getQualifiedName().toString());
        }

        cu.addImport("io.roastedroot.quickjs4j.core.Builtins");
        cu.addImport("io.roastedroot.quickjs4j.core.HostFunction");
        cu.addImport("io.roastedroot.quickjs4j.core.HostRef");
        cu.addImport(List.class);

        var typeName = type.getSimpleName().toString();
        var processorName = new StringLiteralExpr(getClass().getName());
        var classDef =
                cu.addClass(typeName + "_Builtins")
                        .setPublic(true)
                        .setFinal(true)
                        .addSingleMemberAnnotation(Generated.class, processorName);

        classDef.addConstructor().setPrivate(true);

        var newJsFunctions =
                new ArrayCreationExpr(
                        parseType("HostFunction"),
                        new NodeList<>(new ArrayCreationLevel()),
                        new ArrayInitializerExpr(NodeList.nodeList(functions)));

        var builtinsCreationHandle =
                new MethodCallExpr(
                        new MethodCallExpr(
                                new MethodCallExpr(
                                        new NameExpr("Builtins"),
                                        new SimpleName("builder"),
                                        NodeList.nodeList(new StringLiteralExpr(name))),
                                new SimpleName("add"),
                                NodeList.nodeList(newJsFunctions)),
                        new SimpleName("build"),
                        NodeList.nodeList());

        classDef.addMethod("toBuiltins")
                .setPublic(true)
                .setStatic(true)
                .addParameter(typeName, "jsModule")
                .setType("Builtins")
                .setBody(new BlockStmt(new NodeList<>(new ReturnStmt(builtinsCreationHandle))));

        String prefix = (pkg.isUnnamed()) ? "" : packageName + ".";
        String qualifiedName = prefix + type.getSimpleName() + "_Builtins";
        try (Writer writer = filer().createSourceFile(qualifiedName, type).openWriter()) {
            writer.write(cu.printer(printer()).toString());
        } catch (IOException e) {
            log(ERROR, format("Failed to create %s file: %s", qualifiedName, e), null);
        }

        try (Writer writer =
                filer().createResource(
                                StandardLocation.CLASS_OUTPUT,
                                "",
                                "META-INF/quickjs4j/" + name + ".mjs")
                        .openWriter()) {
            writer.write(mjsBuilder.toString());
        } catch (IOException e) {
            log(ERROR, format("Failed to create %s file: %s", qualifiedName, e), null);
        }
    }

    private void addPrimitiveParam(
            String typeLiteral, NodeList<Expression> paramTypes, NodeList<Expression> arguments) {
        var type = parseType(typeLiteral);
        arguments.add(new CastExpr(type, argExpr(paramTypes.size())));
        paramTypes.add(new FieldAccessExpr(new NameExpr(typeLiteral), "class"));
    }

    private Expression addPrimitiveReturn(String typeLiteral) {
        return new FieldAccessExpr(new NameExpr(typeLiteral), "class");
    }

    private boolean extractHasReturn(ExecutableElement executable) {
        String returnName = executable.getReturnType().toString();
        return !returnName.equals("void");
    }

    private Expression extractReturn(ExecutableElement executable) {
        String returnName = executable.getReturnType().toString();
        Expression returnType;
        switch (returnName) {
            case "void":
                returnType = new FieldAccessExpr(new NameExpr("java.lang.Void"), "class");
                break;
            case "int":
                returnType = addPrimitiveReturn("java.lang.Integer");
                break;
            case "long":
                returnType = addPrimitiveReturn("java.lang.Long");
                break;
            case "double":
                returnType = addPrimitiveReturn("java.lang.Double");
                break;
            case "float":
                returnType = addPrimitiveReturn("java.lang.Float");
                break;
            case "boolean":
                returnType = addPrimitiveReturn("java.lang.Boolean");
                break;
            default:
                if (annotatedWith(executable, ReturnsHostRef.class)) {
                    var javaRefType = "io.roastedroot.quickjs4j.core.HostRef";
                    returnType = new FieldAccessExpr(new NameExpr(javaRefType), "class");
                } else {
                    returnType = new FieldAccessExpr(new NameExpr(returnName), "class");
                }
                break;
        }
        return returnType;
    }

    private Expression processHostFunction(
            ExecutableElement executable, String moduleName, StringBuilder mjsBuilder) {
        // compute function name
        var name = executable.getAnnotation(HostFunction.class).value();
        if (name.isEmpty()) {
            name = executable.getSimpleName().toString();
        }

        // compute parameter types and argument conversions
        NodeList<Expression> paramTypes = new NodeList<>();
        // duplicated to automatically compute arguments
        NodeList<Expression> arguments = new NodeList<>();
        for (VariableElement parameter : executable.getParameters()) {
            switch (parameter.asType().toString()) {
                case "int":
                    addPrimitiveParam("java.lang.Integer", paramTypes, arguments);
                    break;
                case "long":
                    addPrimitiveParam("java.lang.Long", paramTypes, arguments);
                    break;
                case "double":
                    addPrimitiveParam("java.lang.Double", paramTypes, arguments);
                    break;
                case "float":
                    addPrimitiveParam("java.lang.Float", paramTypes, arguments);
                    break;
                case "boolean":
                    addPrimitiveParam("java.lang.Boolean", paramTypes, arguments);
                    break;
                default:
                    var typeLiteral = parameter.asType().toString();
                    var type = parseType(parameter.asType().toString());
                    arguments.add(new CastExpr(type, argExpr(paramTypes.size())));
                    if (annotatedWith(parameter, HostRefParam.class)) {
                        var javaRefType = "io.roastedroot.quickjs4j.core.HostRef";
                        paramTypes.add(new FieldAccessExpr(new NameExpr(javaRefType), "class"));
                    } else {
                        paramTypes.add(new FieldAccessExpr(new NameExpr(typeLiteral), "class"));
                    }
            }
        }

        // compute return type and conversion
        Expression returnType = extractReturn(executable);
        boolean hasReturn = extractHasReturn(executable);

        // function invocation
        Expression invocation =
                new MethodCallExpr(
                        new NameExpr("jsModule"), executable.getSimpleName().toString(), arguments);

        // convert return value
        BlockStmt handleBody = new BlockStmt();
        if (!hasReturn) {
            handleBody.addStatement(invocation).addStatement(new ReturnStmt(new NullLiteralExpr()));
        } else {
            var result =
                    new VariableDeclarator(
                            parseType(executable.getReturnType().toString()), "result", invocation);
            handleBody
                    .addStatement(new ExpressionStmt(new VariableDeclarationExpr(result)))
                    .addStatement(new ReturnStmt(new NameExpr("result")));
        }

        // lambda for js function binding
        var handle =
                new LambdaExpr()
                        .addParameter(new Parameter(parseType("List<Object>"), "args"))
                        .setEnclosingParameters(true)
                        .setBody(handleBody);

        // create Js function
        var function =
                new ObjectCreationExpr()
                        .setType("HostFunction")
                        .addArgument(new StringLiteralExpr(name))
                        .addArgument(new MethodCallExpr(new NameExpr("List"), "of", paramTypes))
                        .addArgument(returnType)
                        .addArgument(handle);

        function.setLineComment("");

        // generating the .mjs file
        StringBuilder jsParams = new StringBuilder();
        for (int i = 0; i < paramTypes.size(); i++) {
            if (i > 0) {
                jsParams.append(", ");
            }
            jsParams.append("args" + i);
        }
        mjsBuilder.append("export function " + name + "(" + jsParams + ") {\n");
        String baseInvoke = moduleName + "." + name + "(" + jsParams + ")";
        if (hasReturn) {
            mjsBuilder.append("  return " + baseInvoke + ";\n");
        } else {
            mjsBuilder.append("  " + baseInvoke + ";\n");
        }
        mjsBuilder.append("}\n");

        return function;
    }

    NodeList<Expression> extractParameters(ExecutableElement executable) {
        // compute parameter types and argument conversions
        NodeList<Expression> paramTypes = new NodeList<>();
        for (VariableElement parameter : executable.getParameters()) {
            switch (parameter.asType().toString()) {
                case "int":
                    paramTypes.add(new FieldAccessExpr(new NameExpr("java.lang.Integer"), "class"));
                    break;
                case "long":
                    paramTypes.add(new FieldAccessExpr(new NameExpr("java.lang.Long"), "class"));
                    break;
                case "double":
                    paramTypes.add(new FieldAccessExpr(new NameExpr("java.lang.Double"), "class"));
                    break;
                case "float":
                    paramTypes.add(new FieldAccessExpr(new NameExpr("java.lang.Float"), "class"));
                    break;
                case "boolean":
                    paramTypes.add(new FieldAccessExpr(new NameExpr("java.lang.Boolean"), "class"));
                    break;
                default:
                    var typeLiteral = parameter.asType().toString();
                    if (annotatedWith(parameter, HostRefParam.class)) {
                        var javaRefType = "io.roastedroot.quickjs4j.core.HostRef";
                        paramTypes.add(new FieldAccessExpr(new NameExpr(javaRefType), "class"));
                    } else {
                        paramTypes.add(new FieldAccessExpr(new NameExpr(typeLiteral), "class"));
                    }
            }
        }
        return paramTypes;
    }

    private Expression processGuestFunction(ExecutableElement executable) {
        // compute function name
        var name = executable.getAnnotation(GuestFunction.class).value();
        if (name.isEmpty()) {
            name = executable.getSimpleName().toString();
        }

        // compute parameter types and argument conversions
        NodeList<Expression> paramTypes = extractParameters(executable);

        // compute return type and conversion
        String returnName = executable.getReturnType().toString();
        Expression returnType = extractReturn(executable);

        // create Js function
        var function =
                new ObjectCreationExpr()
                        .setType("GuestFunction")
                        .addArgument(new StringLiteralExpr(name))
                        .addArgument(new MethodCallExpr(new NameExpr("List"), "of", paramTypes))
                        .addArgument(returnType);

        function.setLineComment("");
        return function;
    }

    private static Expression argExpr(int n) {
        return new MethodCallExpr(
                new NameExpr("args"),
                new SimpleName("get"),
                NodeList.nodeList(new IntegerLiteralExpr(String.valueOf(n))));
    }

    private static boolean annotatedWith(Element element, Class<? extends Annotation> annotation) {
        var annotationName = annotation.getName();
        return element.getAnnotationMirrors().stream()
                .map(AnnotationMirror::getAnnotationType)
                .map(TypeMirror::toString)
                .anyMatch(annotationName::equals);
    }
}
