package io.roastedroot.quickjs4j.processor;

import static com.github.javaparser.printer.configuration.DefaultPrinterConfiguration.ConfigOption.COLUMN_ALIGN_PARAMETERS;

import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import io.roastedroot.quickjs4j.annotations.Builtins;
import io.roastedroot.quickjs4j.annotations.Invokables;
import io.roastedroot.quickjs4j.annotations.ScriptInterface;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

public abstract class Quickjs4jAbstractProcessor extends AbstractProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                Builtins.class.getName(),
                Invokables.class.getName(),
                ScriptInterface.class.getName());
    }

    static final class AbortProcessingException extends RuntimeException {}

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

    protected boolean extractHasReturn(ExecutableElement executable) {
        String returnName = executable.getReturnType().toString();
        return !returnName.equals("void");
    }

    protected static boolean annotatedWith(
            Element element, Class<? extends Annotation> annotation) {
        var annotationName = annotation.getName();
        return element.getAnnotationMirrors().stream()
                .map(AnnotationMirror::getAnnotationType)
                .map(TypeMirror::toString)
                .anyMatch(annotationName::equals);
    }
}
