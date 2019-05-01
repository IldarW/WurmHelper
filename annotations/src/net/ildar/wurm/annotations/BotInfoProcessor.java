package net.ildar.wurm.annotations;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("net.ildar.wurm.annotations.BotInfo")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class BotInfoProcessor extends AbstractProcessor {
    private List<AnnotatedBot> annotatedBots = new ArrayList<>();
    private Elements elementUtils;
    private Filer filer;
    private Messager messager;
    private Types typeUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        elementUtils = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
        typeUtils = processingEnv.getTypeUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        boolean updatedBotList = false;
        for (TypeElement annotation : annotations) {
            if (annotation.getQualifiedName().toString().equals(BotInfo.class.getCanonicalName())) {
                Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
                for (Element annotatedElement : annotatedElements) {
                    if (annotatedElement.getKind() != ElementKind.CLASS) {
                        error(annotatedElement, "Аннотацию %s допускается использовать только на классах", BotInfo.class.getSimpleName());
                        return true;
                    }
                    TypeElement typeElement = (TypeElement) annotatedElement;
                    DeclaredType botType = typeUtils.getDeclaredType(elementUtils.getTypeElement("net.ildar.wurm.bot.Bot"));
                    if (!typeUtils.isSubtype(typeElement.asType(), botType)) {
                        error(annotatedElement, "Аннотацию %s допускается использовать только на классах-потомках Bot", BotInfo.class.getSimpleName());
                        return true;
                    }
                    BotInfo annotationObject = typeElement.getAnnotation(BotInfo.class);
                    annotatedBots.add(new AnnotatedBot(annotationObject, typeElement.getQualifiedName().toString()));
                    updatedBotList = true;
                }
            }
        }
        if (!updatedBotList) {
            try {
                JavaFileObject sourceFile = filer.createSourceFile("net.ildar.wurm.BotRegistrationProvider");
                try(Writer writer = sourceFile.openWriter()) {
                    addLine(writer, "package net.ildar.wurm;");
                    addLine(writer, "");
                    addLine(writer, "import java.util.*;");
                    addLine(writer, "");
                    addLine(writer, "class BotRegistrationProvider {");
                    addLine(writer, "   static List<BotRegistration> getBotList() {");
                    addLine(writer, "       List<BotRegistration> registrations = new ArrayList<>();");
                    addLine(writer, "       try {");
                    for(AnnotatedBot annotatedBot : annotatedBots) {
                        addLine(writer, String.format("         registrations.add(new BotRegistration(Class.forName(\"%s\"), \"%s\", \"%s\"));", annotatedBot.botClass, prepareDescription(annotatedBot.botInfo.description()), annotatedBot.botInfo.abbreviation()));
                    }
                    addLine(writer, "       } catch (ClassNotFoundException e) {");
                    addLine(writer, "           e.printStackTrace();");
                    addLine(writer, "       }");
                    addLine(writer, "       return registrations;");
                    addLine(writer, "   }");
                    addLine(writer, "}");
                }
            } catch (IOException e) {
                throw new RuntimeException("Ошибка генерации кода", e);
            }
        }

        return true;
    }

    private String prepareDescription(String description) {
        if (description == null)
            return description;
        return description.replaceAll("\"", "\\\\\"");
    }

    private void addLine(Writer writer, String line) throws IOException{
        writer.append(line).append(System.lineSeparator());
    }

    private void error(Element e, String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), e);
    }

}