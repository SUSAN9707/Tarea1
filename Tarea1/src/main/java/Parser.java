import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private int index = 0;
    private boolean huboErrores = false;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public void parse() {
        json(); // símbolo inicial
        if (!match("EOF")) {
            error("EOF");
            huboErrores = true;
        }

        if (huboErrores) {
            System.out.println("El archivo JSON es sintácticamente incorrecto.");
        } else {
            System.out.println("El archivo JSON es sintácticamente correcto.");
        }
    }

    // --------------------- Reglas de producción -------------------------

    private void json() {
        elemento();
    }

    private void elemento() {
        if (check("{")) {
            objeto();
        } else if (check("[")) {
            arreglo();
        } else {
            error("elemento (objeto o arreglo)");
            sincronizar("{", "[");
        }
    }

    private void objeto() {
        if (match("{")) {
            if (check("}")) {
                match("}");
            } else {
                atributos();
                match("}");
            }
        } else {
            error("{");
            sincronizar("{");
        }
    }

    private void atributos() {
        atributo();
        while (match(",")) {
            atributo();
        }
    }

    private void atributo() {
        if (!match("STRING")) {
            error("STRING (clave)");
            sincronizar(":");
        }
        if (!match(":")) {
            error("':'");
            sincronizar("{", "[", "STRING", "NUMBER", "PR_TRUE", "PR_FALSE", "PR_NULL");
        }
        valor();
    }

    private void arreglo() {
        if (match("[")) {
            if (check("]")) {
                match("]");
            } else {
                elementos();
                match("]");
            }
        } else {
            error("[");
            sincronizar("[");
        }
    }

    private void elementos() {
        valor();
        while (match(",")) {
            valor();
        }
    }

    private void valor() {
        if (check("{")) {
            objeto();
        } else if (check("[")) {
            arreglo();
        } else if (match("STRING") || match("NUMBER") || match("PR_TRUE") || match("PR_FALSE") || match("PR_NULL")) {
            // válido
        } else {
            error("valor (objeto, arreglo, string, número, booleano o null)");
            sincronizar(",", "]", "}");
        }
    }

    // ---------------------- Utilidades -------------------------------

    private boolean match(String tipo) {
        if (check(tipo)) {
            index++;
            return true;
        }
        return false;
    }

    private boolean check(String tipo) {
        if (isAtEnd()) return false;
        return tokens.get(index).getTipo().equals(tipo);
    }

    private void error(String esperado) {
        Token actual = isAtEnd() ? new Token("EOF", "EOF") : tokens.get(index);
        System.err.println("Error: se esperaba " + esperado + " en " + actual.getValor());
        huboErrores = true;
    }

    private void sincronizar(String... tipos) {
        while (!isAtEnd()) {
            for (String tipo : tipos) {
                if (check(tipo)) return;
            }
            index++;
        }
    }

    private boolean isAtEnd() {
        return index >= tokens.size();
    }
}
