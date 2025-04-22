package io.roastedroot.js.processor;

import static com.github.javaparser.StaticJavaParser.parseType;
import static com.github.javaparser.printer.configuration.DefaultPrinterConfiguration.ConfigOption.COLUMN_ALIGN_PARAMETERS;
import static java.lang.String.format;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;

import com.github.javaparser.ast.ArrayCreationLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
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
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import io.roastedroot.js.annotations.JavaRefParam;
import io.roastedroot.js.annotations.JsFunction;
import io.roastedroot.js.annotations.JsModule;
import io.roastedroot.js.annotations.ReturningJavaRef;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
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
        return Set.of(JsModule.class.getName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(JsModule.class)) {
            log(NOTE, "Generating Js bindings for " + element, null);
            try {
                processModule((TypeElement) element);
            } catch (AbortProcessingException e) {
                // skip type
            }
        }

        return false;
    }

    private static class GeneratedJsFunc {
        final String name;
        final Function<Integer, Expression> bodyGen;

        GeneratedJsFunc(String name, Function<Integer, Expression> bodyGen) {
            this.name = name;
            this.bodyGen = bodyGen;
        }
    }

    private void processModule(TypeElement type) {
        List<Expression> functions = new ArrayList<>();
        int functionIndex = 0;
        for (Element member : elements().getAllMembers(type)) {
            if (member instanceof ExecutableElement && annotatedWith(member, JsFunction.class)) {
                functions.add(processMethod((ExecutableElement) member, functionIndex++));
            }
        }

        var pkg = getPackageName(type);
        var packageName = pkg.getQualifiedName().toString();
        var cu = (pkg.isUnnamed()) ? new CompilationUnit() : new CompilationUnit(packageName);
        if (!pkg.isUnnamed()) {
            cu.setPackageDeclaration(packageName);
            cu.addImport(type.getQualifiedName().toString());
        }

        cu.addImport("io.roastedroot.js.Builtins");
        cu.addImport("io.roastedroot.js.JsFunction");
        cu.addImport("io.roastedroot.js.JavaRef");
        cu.addImport("java.util.List");

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
                        parseType("JsFunction"),
                        new NodeList<>(new ArrayCreationLevel()),
                        new ArrayInitializerExpr(NodeList.nodeList(functions)));

        classDef.addMethod("toBuiltins")
                .setPublic(true)
                .setStatic(true)
                .addParameter(typeName, "jsModule")
                .setType("JsFunction[]")
                .setBody(new BlockStmt(new NodeList<>(new ReturnStmt(newJsFunctions))));

        String prefix = (pkg.isUnnamed()) ? "" : packageName + ".";
        String qualifiedName = prefix + type.getSimpleName() + "_Builtins";
        try (Writer writer = filer().createSourceFile(qualifiedName, type).openWriter()) {
            writer.write(cu.printer(printer()).toString());
        } catch (IOException e) {
            log(ERROR, format("Failed to create %s file: %s", qualifiedName, e), null);
        }
    }

    private Expression processMethod(ExecutableElement executable, int functionIndex) {
        // compute function name
        var name = executable.getAnnotation(JsFunction.class).value();

        // compute parameter types and argument conversions
        NodeList<Expression> paramTypes = new NodeList<>();
        NodeList<Expression> arguments = new NodeList<>();
        for (VariableElement parameter : executable.getParameters()) {
            Expression argExpr = argExpr(paramTypes.size());
            switch (parameter.asType().toString()) {
                case "int":
                    {
                        var typeLiteral = "java.lang.Integer";
                        var type = parseType(typeLiteral);
                        paramTypes.add(new FieldAccessExpr(new NameExpr(typeLiteral), "class"));
                        arguments.add(new CastExpr(type, argExpr));
                        break;
                    }
                default:
                    var typeLiteral = parameter.asType().toString();
                    var type = parseType(parameter.asType().toString());
                    if (annotatedWith(parameter, JavaRefParam.class)) {
                        var javaRefType = "io.roastedroot.js.JavaRef";
                        paramTypes.add(new FieldAccessExpr(new NameExpr(javaRefType), "class"));
                        arguments.add(new CastExpr(type, argExpr));
                    } else {
                        paramTypes.add(new FieldAccessExpr(new NameExpr(typeLiteral), "class"));
                        arguments.add(new CastExpr(type, argExpr));
                    }
            }
        }

        // compute return type and conversion
        String returnName = executable.getReturnType().toString();
        Expression returnType;
        boolean hasReturn;
        switch (returnName) {
            case "void":
                returnType = new FieldAccessExpr(new NameExpr("java.lang.Void"), "class");
                hasReturn = false;
                break;
            case "int":
                returnType = new FieldAccessExpr(new NameExpr("java.lang.Integer"), "class");
                hasReturn = true;
                break;
            default:
                hasReturn = true;
                if (annotatedWith(executable, ReturningJavaRef.class)) {
                    var javaRefType = "io.roastedroot.js.JavaRef";
                    returnType = new FieldAccessExpr(new NameExpr(javaRefType), "class");
                } else {
                    returnType = new FieldAccessExpr(new NameExpr(returnName), "class");
                }
                break;
        }

        // function invocation
        Expression invocation =
                new MethodCallExpr(
                        new NameExpr("jsModule"), executable.getSimpleName().toString(), arguments);

        // convert return value
        BlockStmt handleBody = new BlockStmt();
        if (!hasReturn) {
            handleBody.addStatement(invocation).addStatement(new ReturnStmt(new NullLiteralExpr()));
        } else {
            var result = new VariableDeclarator(parseType(returnName), "result", invocation);
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
                        .setType("JsFunction")
                        .addArgument(new StringLiteralExpr(name))
                        .addArgument(new IntegerLiteralExpr(functionIndex))
                        .addArgument(new MethodCallExpr(new NameExpr("List"), "of", paramTypes))
                        .addArgument(returnType)
                        .addArgument(handle);

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
