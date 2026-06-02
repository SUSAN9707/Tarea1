import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class Translator {

    private static final String OUTPUT_PATH = "src/main/resources/output.xml";

    private final List<Token> tokens;
    private int index = 0;
    private boolean huboErrores = false;

    public Translator(List<Token> tokens) {
        this.tokens = tokens;
    }

    // -----------------------------------------------------------------
    // Punto de entrada
    // -----------------------------------------------------------------

    public void translate() {
        String resultado = json();

        if (!match("EOF")) {
            error("EOF");
        }

        if (huboErrores) {
            System.out.println("Traduccion completada con errores (se aplico Panic Mode).");
        } else {
            System.out.println("Traduccion exitosa.");
        }

        guardarXml(resultado);
    }

    // -----------------------------------------------------------------
    // Reglas de produccion — cada una retorna su fragmento XML
    // -----------------------------------------------------------------

    // json -> elemento
    private String json() {
        return elemento();
    }

    // elemento -> objeto | arreglo
    private String elemento() {
        if (check("{")) {
            return objeto(null);
        } else if (check("[")) {
            return arreglo(null);
        } else {
            error("elemento (objeto o arreglo)");
            sincronizar("{", "[");
            return "";
        }
    }

    // objeto: { atributos? }
    // tagName es el nombre de la etiqueta envolvente (null = objeto raiz anonimo)
    private String objeto(String tagName) {
        StringBuilder sb = new StringBuilder();

        if (!match("{")) {
            error("{");
            sincronizar("{", "}", "EOF");
            return "";
        }

        if (check("}")) {
            // Objeto vacio
            match("}");
            return "";     // el llamador decide como envolver el contenido vacio
        }

        // Atributos sintetizados: cada atributo contribuye su linea XML
        sb.append(atributos());

        if (!match("}")) {
            error("'}'");
        }

        return sb.toString();
    }

    // atributos: atributo (, atributo)*
    private String atributos() {
        StringBuilder sb = new StringBuilder();
        sb.append(atributo());

        while (!check("}") && !check("EOF") && !isAtEnd()) {
            if (match(",")) {
                if (check("}")) {
                    error("STRING (clave) despues de ','");
                    return sb.toString();
                }
                sb.append(atributo());
            } else if (check("STRING")) {
                error("',' entre atributos");
                sb.append(atributo());
            } else {
                error("',' o '}'");
                sincronizar(",", "}", "EOF");
                if (match(",")) {
                    if (!check("}") && !check("EOF")) {
                        sb.append(atributo());
                    }
                }
            }
        }

        return sb.toString();
    }

    // atributo: STRING : valor
    // Atributo sintetizado: nombre de clave se convierte en etiqueta XML
    private String atributo() {
        String nombre = "unknown";    // salvaguarda ante error lexico

        if (check("STRING")) {
            String lexema = tokens.get(index).getValor();
            // Extraer el nombre sin comillas: "personas" -> personas
            nombre = lexema.replaceAll("^\"|\"$", "");
            match("STRING");
        } else {
            error("STRING (clave)");
            sincronizar(":");
        }

        if (!match(":")) {
            error("':'");
            sincronizar("{", "[", "STRING", "NUMBER", "PR_TRUE", "PR_FALSE", "PR_NULL");
        }

        // El valor produce su contenido; lo envolvemos en <nombre>...</nombre>
        String contenido = valor(nombre);
        return contenido;
    }

    // arreglo: [ elementos? ]
    // tagName = nombre de la etiqueta padre (null si es el arreglo raiz)
    private String arreglo(String tagName) {
        if (!match("[")) {
            error("[");
            sincronizar("[", "]", "EOF");
            return tagName != null ? "<" + tagName + "></" + tagName + ">" : "";
        }

        if (check("]")) {
            // Arreglo vacio
            match("]");
            return tagName != null ? "<" + tagName + "></" + tagName + ">" : "";
        }

        String cuerpo = elementos();

        if (!match("]")) {
            error("']'");
        }

        // El arreglo se envuelve en la etiqueta del atributo padre
        if (tagName != null) {
            return "<" + tagName + ">\n" + indentar(cuerpo) + "</" + tagName + ">\n";
        }
        return cuerpo;
    }

    // elementos: valor (, valor)*
    private String elementos() {
        StringBuilder sb = new StringBuilder();
        sb.append(valor(null));

        while (!check("]") && !check("EOF") && !isAtEnd()) {
            if (match(",")) {
                if (check("]")) {
                    error("valor despues de ','");
                    return sb.toString();
                }
                sb.append(valor(null));
            } else {
                error("',' o ']'");
                sincronizar(",", "]", "EOF");
                if (match(",")) {
                    if (!check("]") && !check("EOF")) {
                        sb.append(valor(null));
                    }
                }
            }
        }

        return sb.toString();
    }

    // valor: objeto | arreglo | literal
    // tagName = nombre de la etiqueta envolvente (null si es elemento de arreglo sin nombre)
    private String valor(String tagName) {
        if (check("{")) {
            // Objeto: su contenido queda envuelto en <tagName> o en <item>
            String etiqueta = tagName != null ? tagName : "item";
            String cuerpo = objeto(etiqueta);
            if (cuerpo.isEmpty()) {
                return "<" + etiqueta + "></" + etiqueta + ">\n";
            }
            return "<" + etiqueta + ">\n" + indentar(cuerpo) + "</" + etiqueta + ">\n";

        } else if (check("[")) {
            // Arreglo: se delega; arreglo() gestiona la etiqueta envolvente
            return arreglo(tagName);

        } else if (check("STRING") || check("NUMBER") || check("PR_TRUE")
                || check("PR_FALSE") || check("PR_NULL")) {

            String lexema = tokens.get(index).getValor();
            match(tokens.get(index).getTipo());   // consume el token

            if (tagName != null) {
                // Valor de un atributo: <nombre>valor</nombre>
                return "<" + tagName + ">" + lexema + "</" + tagName + ">\n";
            } else {
                // Elemento primitivo dentro de un arreglo (sin nombre de clave)
                return "<item>" + lexema + "</item>\n";
            }

        } else {
            error("valor (objeto, arreglo, string, numero, booleano o null)");
            sincronizar(",", "]", "}");
            return "";
        }
    }

    // -----------------------------------------------------------------
    // Utilidades de parser
    // -----------------------------------------------------------------

    private boolean match(String tipo) {
        if (check(tipo)) {
            index++;
            return true;
        }
        return false;
    }

    private boolean check(String esperado) {
        if (isAtEnd()) return false;
        Token actual = tokens.get(index);
        return actual.getTipo().equals(esperado) || actual.getValor().equals(esperado);
    }

    private boolean isAtEnd() {
        return index >= tokens.size();
    }

    private void error(String esperado) {
        Token actual = isAtEnd() ? new Token("EOF", "EOF") : tokens.get(index);
        System.err.println("Error de traduccion: se esperaba " + esperado
                + " pero se encontro '" + actual.getValor() + "'");
        huboErrores = true;
    }

    // Panic Mode: avanza hasta encontrar uno de los tokens de sincronizacion
    private void sincronizar(String... tipos) {
        while (!isAtEnd()) {
            for (String tipo : tipos) {
                if (check(tipo)) return;
            }
            index++;
        }
    }

    // -----------------------------------------------------------------
    // Utilidades de formato XML
    // -----------------------------------------------------------------

    // Agrega un nivel de indentacion (tab) a cada linea no vacia
    private String indentar(String bloque) {
        if (bloque == null || bloque.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String linea : bloque.split("\n", -1)) {
            if (!linea.isEmpty()) {
                sb.append("\t").append(linea).append("\n");
            }
        }
        return sb.toString();
    }

    private void guardarXml(String contenido) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_PATH))) {
            writer.write(contenido);
        } catch (IOException e) {
            System.err.println("Error escribiendo archivo XML: " + e.getMessage());
        }
    }
}
