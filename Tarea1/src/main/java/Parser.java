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
                consume("}", "'}'");
            }
        } else {
            error("{");
            sincronizar("{", "}", "EOF");
        }
    }
    private void consume(String tipo, String esperado) {
        if (!match(tipo)) {
            error(esperado);
        }
    }
    private void atributos() {
        atributo();

        while (!check("}") && !check("EOF") && !isAtEnd()) {
            if (match(",")) {
                if (check("}")) {
                    error("STRING (clave) después de ','");
                    return;
                }
                atributo();
            } else if (check("STRING")) {
                error("',' entre atributos");
                atributo(); // continúa para no trabarse
            } else {
                error("',' o '}'");
                sincronizar(",", "}", "EOF");

                if (match(",")) {
                    if (!check("}") && !check("EOF")) {
                        atributo();
                    }
                }
            }
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
                consume("]", "']'");
            }
        } else {
            error("[");
            sincronizar("[", "]", "EOF");
        }
    }

    private void elementos() {
        valor();

        while (!check("]") && !check("EOF") && !isAtEnd()) {
            if (match(",")) {
                if (check("]")) {
                    error("valor después de ','");
                    return;
                }
                valor();
            } else {
                error("',' o ']'");
                sincronizar(",", "]", "EOF");

                if (match(",")) {
                    if (!check("]") && !check("EOF")) {
                        valor();
                    }
                }
            }
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

    private boolean check(String esperado) {
        if (isAtEnd()) return false;

        Token actual = tokens.get(index);

        return actual.getTipo().equals(esperado) || actual.getValor().equals(esperado);
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
