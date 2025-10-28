package io.roastedroot.quickjs4j.processor;

import static com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType;
import static com.github.javaparser.StaticJavaParser.parseType;
import static java.lang.String.format;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;

import com.github.javaparser.ast.ArrayCreationLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import io.roastedroot.quickjs4j.annotations.GuestFunction;
import io.roastedroot.quickjs4j.annotations.HostRefParam;
import io.roastedroot.quickjs4j.annotations.Invokables;
import io.roastedroot.quickjs4j.annotations.ReturnsHostRef;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Generated;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

public final class InvokablesProcessor extends Quickjs4jAbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
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

    private Expression addPrimitiveReturn(String typeLiteral) {
        return new FieldAccessExpr(new NameExpr(typeLiteral), "class");
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
                var typeLiteral = removeGenerics(returnName);
                if (annotatedWith(executable, ReturnsHostRef.class)) {
                    var javaRefType = "io.roastedroot.quickjs4j.core.HostRef";
                    returnType = new FieldAccessExpr(new NameExpr(javaRefType), "class");
                } else {
                    returnType = new FieldAccessExpr(new NameExpr(typeLiteral), "class");
                }
                break;
        }
        return returnType;
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
                    var typeLiteral = removeGenerics(parameter.asType().toString());
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

    private String removeGenerics(String typeLiteral) {
        var genericIndex = typeLiteral.indexOf('<');
        if (genericIndex > 0) {
            return typeLiteral.substring(0, genericIndex);
        } else {
            return typeLiteral;
        }
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
}
