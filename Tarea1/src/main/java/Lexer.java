import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class Lexer {
    // Definición de las rutas para el archivo de entrada y salida
    private static final String INPUT_PATH = "src/main/resources/fuente.txt";
    private static final String OUTPUT_PATH = "src/main/resources/output.txt";
    private static final List<Token> tokens = new ArrayList<>(); // Lista para almacenar los tokens encontrados

    // Mapa que relaciona cada tipo de token con su expresión regular correspondiente
    private static final Map<String, Pattern> patterns = new LinkedHashMap<>();

   /* static {
        // Inicialización de los patrones para los diferentes tipos de tokens (ej. corchetes, números, etc.)
        patterns.put("L_CORCHETE", Pattern.compile("^\\[")); // Token para el corchete izquierdo
        patterns.put("R_CORCHETE", Pattern.compile("^\\]")); // Token para el corchete derecho
        patterns.put("L_LLAVE", Pattern.compile("^\\{")); // Token para la llave izquierda
        patterns.put("R_LLAVE", Pattern.compile("^\\}")); // Token para la llave derecha
        patterns.put("COMA", Pattern.compile("^,")); // Token para la coma
        patterns.put("DOS_PUNTOS", Pattern.compile("^:")); // Token para los dos puntos
        patterns.put("STRING", Pattern.compile("^\"[^\"]*\"")); // Token para los strings (comillas dobles)
        patterns.put("NUMBER", Pattern.compile("^[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?")); // Token para números (enteros y flotantes)
        patterns.put("PR_TRUE", Pattern.compile("^(?i)true")); // Token para el valor booleano 'true'
        patterns.put("PR_FALSE", Pattern.compile("^(?i)false")); // Token para el valor booleano 'false'
        patterns.put("PR_NULL", Pattern.compile("^(?i)null")); // Token para el valor nulo 'null'
    }*/

    static {
        patterns.put("{", Pattern.compile("^\\{"));
        patterns.put("}", Pattern.compile("^\\}"));
        patterns.put("[", Pattern.compile("^\\["));
        patterns.put("]", Pattern.compile("^\\]"));
        patterns.put(",", Pattern.compile("^,"));
        patterns.put(":", Pattern.compile("^:"));

// Palabras reservadas primero
        patterns.put("PR_TRUE", Pattern.compile("^(?i)true\\b"));
        patterns.put("PR_FALSE", Pattern.compile("^(?i)false\\b"));
        patterns.put("PR_NULL", Pattern.compile("^(?i)null\\b"));

// Luego STRING y NUMBER
        patterns.put("STRING", Pattern.compile("^\"[^\"]*\""));
        patterns.put("NUMBER", Pattern.compile("^[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?"));
    }

    public static void main(String[] args) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(INPUT_PATH));
            analizar(lines); // tu función original con sangrías
            guardarTokens(); // salida formateada con indentación

            // Usamos la nueva función para pasarle solo los tokens limpios al parser
            List<Token> tokensParaParser = analizarParaParser(INPUT_PATH);
            Parser parser = new Parser(tokensParaParser);
            parser.parse();

        } catch (IOException e) {
            System.err.println("Error leyendo archivo fuente: " + e.getMessage());
        }
    }

    // Método para realizar el análisis léxico de las líneas del archivo
    private static void analizar(List<String> lineas) {
        for (String linea : lineas) {
            StringBuilder resultadoLinea = new StringBuilder(); // Construir la representación de los tokens por línea

            // Extraer la sangría (espacios o tabulaciones al inicio de la línea)
            String sangria = linea.replaceAll("^([ \\t]*).*", "$1");
            String contenido = linea.stripLeading(); // Eliminar la sangría de la línea

            while (!contenido.isEmpty()) {
                boolean match = false; // Bandera para verificar si se encuentra un token válido

                // Buscar un token válido en el contenido de la línea
                for (var entry : patterns.entrySet()) {
                    Matcher matcher = entry.getValue().matcher(contenido); // Intentar hacer coincidir el patrón
                    if (matcher.find()) {
                        // Si se encuentra un patrón, obtener el lexema y agregar el tipo de token al resultado
                        String lexema = matcher.group();
                        resultadoLinea.append(entry.getKey()).append(" ");
                        contenido = contenido.substring(lexema.length()).stripLeading(); // Eliminar el lexema procesado
                        match = true;
                        break; // Salir del bucle si se encuentra un token
                    }
                }

                // Si no se encuentra un token válido, mostrar error léxico
                if (!match) {
                    System.err.println("Error léxico: símbolo inesperado '" + contenido.charAt(0) + "'");
                    contenido = contenido.substring(1).stripLeading(); // Eliminar el primer carácter inesperado
                }
            }

            // Al finalizar la línea, agregar la línea indented con los tokens encontrados
            tokens.add(new Token("INDENTED_LINE", sangria + resultadoLinea.toString().stripTrailing()));
        }

        // Si se quisiera agregar un token especial para representar el final del archivo
        //tokens.add(new Token("EOF", "EOF"));
    }
    public static List<Token> analizarParaParser(String path) {
        List<Token> tokenList = new ArrayList<>();
        try {
            List<String> lineas = Files.readAllLines(Paths.get(path));
            for (String linea : lineas) {
                String contenido = linea.stripLeading();
                while (!contenido.isEmpty()) {
                    boolean match = false;
                    for (var entry : patterns.entrySet()) {
                        Matcher matcher = entry.getValue().matcher(contenido);
                        if (matcher.find()) {
                            String lexema = matcher.group();
                            tokenList.add(new Token(entry.getKey(), lexema));
                            contenido = contenido.substring(lexema.length()).stripLeading();
                            match = true;
                            break;
                        }
                    }
                    if (!match) {
                        System.err.println("Error léxico: símbolo inesperado '" + contenido.charAt(0) + "'");
                        contenido = contenido.substring(1).stripLeading();
                    }
                }
            }
            tokenList.add(new Token("EOF", "EOF"));
        } catch (IOException e) {
            System.err.println("Error leyendo archivo fuente: " + e.getMessage());
        }
        return tokenList;
    }

    // Método para guardar los tokens encontrados en el archivo de salida
    private static void guardarTokens() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_PATH))) {
            for (Token token : tokens) {
                // Si el token es una línea con sangría, escribir su valor (el tipo y los lexemas)
                if (token.getTipo().equals("INDENTED_LINE")) {
                    writer.write(token.getValor());
                } else {
                    // Para otros tokens, solo escribir su tipo
                    writer.write(token.getTipo());
                }
                writer.newLine(); // Escribir una nueva línea en el archivo de salida
            }
        } catch (IOException e) {
            System.err.println("Error escribiendo archivo de salida: " + e.getMessage()); // Manejo de errores al escribir el archivo
        }
    }
}
