package ast;

import pt.up.fe.comp.jmm.JmmNode;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.*;

/**
 * Data -> {scope, extra_data1, extra_data2}
 */
public class OllirVisitor extends PreorderJmmVisitor<List<Object>, String> {
    private final JmmSymbolTable table;
    private JmmMethod currentMethod;
    private final List<Report> reports;
    private String scope;
    private Set<JmmNode> visited = new HashSet<>();

    private int temp_sequence = 1;

    public OllirVisitor(JmmSymbolTable table, List<Report> reports) {
        super(OllirVisitor::reduce);

        this.table = table;
        this.reports = reports;

        addVisit("ClassDeclaration", this::dealWithClass);
        addVisit("MainMethod", this::dealWithMainDeclaration);
        addVisit("ClassMethod", this::dealWithMethodDeclaration);
        addVisit("Assignment", this::dealWithAssignment);
        addVisit("IntegerLiteral", this::dealWithPrimitive);
        addVisit("BooleanLiteral", this::dealWithPrimitive);
        addVisit("BinaryOperation", this::dealWithBinaryOperation);
        addVisit("Variable", this::dealWithVariable);
        addVisit("Return", this::dealWithReturn);

        addVisit("RelationalExpression", this::dealWithBinaryOperation);
        addVisit("AndExpression", this::dealWithAndExpression);
        addVisit("NotExpression", this::dealWithNotExpression);

        addVisit("IfStatement", this::dealWithIfStatement);
        addVisit("ElseStatement", this::dealWithElseStatement);
        addVisit("IfCondition", this::dealWithCondition);

        setDefaultVisit(this::defaultVisit);
    }

    private String dealWithClass(JmmNode node, List<Object> data) {
        scope = "CLASS";

        StringBuilder ollir = new StringBuilder();

        ollir.append(OllirTemplates.constructor(table.getClassName()));

        for (JmmNode child : node.getChildren()) {
            String ollirChild = visit(child, Arrays.asList("CLASS"));
            if (ollirChild != null && !ollirChild.equals("DEFAULT_VISIT"))
                ollir.append("\n\n").append(ollirChild);
        }

        ollir.append(OllirTemplates.closeBrackets());
        return ollir.toString();
    }

    private String dealWithMainDeclaration(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return "DEFAULT_VISIT";
        visited.add(node);

        scope = "METHOD";

        try {
            currentMethod = table.getMethod("main", Arrays.asList(new Type("String", true)), new Type("void", false));
        } catch (Exception e) {
            currentMethod = null;
            e.printStackTrace();
        }

        StringBuilder builder = new StringBuilder(OllirTemplates.method(
                "main",
                currentMethod.parametersToOllir(),
                OllirTemplates.type(currentMethod.getReturnType()),
                true));

        List<String> body = new ArrayList<>();

        for (JmmNode child : node.getChildren()) {
            String ollirChild = visit(child, Arrays.asList("METHOD"));
            if (ollirChild != null && !ollirChild.equals("DEFAULT_VISIT"))
                if (ollirChild.equals("")) continue;
            body.add(ollirChild);
        }

        builder.append(String.join("\n", body));

        builder.append(OllirTemplates.closeBrackets());

        return builder.toString();
    }

    private String dealWithMethodDeclaration(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return "DEFAULT_VISIT";
        visited.add(node);

        List<Type> params = JmmMethod.parseParameters(node.get("params"));

        try {
            currentMethod = table.getMethod(node.get("name"), params, JmmSymbolTable.getType(node, "return"));
        } catch (Exception e) {
            currentMethod = null;
            e.printStackTrace();
        }

        StringBuilder builder = new StringBuilder(OllirTemplates.method(
                currentMethod.getName(),
                currentMethod.parametersToOllir(),
                OllirTemplates.type(currentMethod.getReturnType())));

        List<String> body = new ArrayList<>();

        for (JmmNode child : node.getChildren()) {
            String ollirChild = visit(child, Arrays.asList("METHOD"));
            if (ollirChild != null && !ollirChild.equals("DEFAULT_VISIT"))
                if (ollirChild.equals("")) continue;
            body.add(ollirChild);
        }

        builder.append(String.join("\n", body));

        builder.append(OllirTemplates.closeBrackets());

        return builder.toString();
    }

    private String dealWithAssignment(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return "DEFAULT_VISIT";
        visited.add(node);

        Map.Entry<Symbol, Boolean> variable;
        if ((variable = currentMethod.getField(node.get("variable"))) == null) {
            variable = table.getField(node.get("variable"));
        }

        StringBuilder ollir = new StringBuilder();

        ollir.append(visit(node.getChildren().get(0), Arrays.asList("ASSIGNMENT", variable.getKey())));

        return ollir.toString();
    }

    private String dealWithPrimitive(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return "DEFAULT_VISIT";
        visited.add(node);

        String value;
        Type type;

        switch (node.getKind()) {
            case "IntegerLiteral":
                value = node.get("value") + ".i32";
                type = new Type("int", false);
                break;
            case "BooleanLiteral":
                value = (node.get("value").equals("true") ? "1" : "0") + ".bool";
                type = new Type("boolean", false);
                break;
            default:
                value = "";
                type = new Type("void", false);
                break;
        }

        if (data.get(0).equals("ASSIGNMENT")) {
            Symbol variable = (Symbol) data.get(1);

            String name = currentMethod.isParameter(variable);

            return String.format("%s :=%s %s;", OllirTemplates.variable(variable, name), OllirTemplates.type(type), value);
        } else {
            return value;
        }
    }

    private String dealWithBinaryOperation(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return "DEFAULT_VISIT";
        visited.add(node);

        Symbol variable = (data.get(0).equals("ASSIGNMENT")) ? (Symbol) data.get(1) : null;
        String name = (variable != null) ? currentMethod.isParameter(variable) : null;

        JmmNode left = node.getChildren().get(0);
        JmmNode right = node.getChildren().get(1);

        String leftReturn;
        String rightReturn;

        // BO and BO
        if (left.getKind().equals("BinaryOperation") && right.getKind().equals("BinaryOperation")) {
            leftReturn = visit(left, Arrays.asList("ASSIGNMENT", new Symbol(new Type("int", false), String.format("temporary%d", temp_sequence++))));
            rightReturn = visit(right, Arrays.asList("ASSIGNMENT", new Symbol(new Type("int", false), String.format("temporary%d", temp_sequence++))));

            String[] parts = leftReturn.split("\n");
            String aux1 = parts[parts.length - 1].split(" :=")[0];

            String[] parts2 = rightReturn.split("\n");
            String aux2 = parts2[parts.length - 1].split(" :=")[0];

            StringBuilder ollir = new StringBuilder();
            ollir.append(leftReturn).append("\n");
            ollir.append(rightReturn).append("\n");

            if (variable == null) {
                ollir.append(String.format("%s %s.i32 %s", aux1, node.get("operation"), aux2));
            } else {
                ollir.append(String.format("%s :=.i32 %s;", OllirTemplates.variable(variable, name),
                        OllirTemplates.binary(aux1, aux2, node.get("operation"), new Type("int", false))));
            }
            return ollir.toString();
        }
        // BO and Simple
        else if (left.getKind().equals("BinaryOperation") && !right.getKind().equals("BinaryOperation")) {
            leftReturn = visit(left, Arrays.asList("ASSIGNMENT", new Symbol(new Type("int", false), String.format("temporary%d", temp_sequence++))));
            rightReturn = visit(right, Arrays.asList("BINARY_OP"));

            String[] parts = leftReturn.split("\n");
            String aux1 = parts[parts.length - 1].split(" :=")[0];

            StringBuilder ollir = new StringBuilder();
            ollir.append(leftReturn).append("\n");

            if (variable == null) {
                ollir.append(String.format("%s %s.i32 %s", aux1, node.get("operation"), rightReturn));
            } else {
                ollir.append(String.format("%s :=.i32 %s;", OllirTemplates.variable(variable, name),
                        OllirTemplates.binary(aux1, rightReturn, node.get("operation"), new Type("int", false))));
            }
            return ollir.toString();
        }
        // Simple and BO
        else if (!left.getKind().equals("BinaryOperation") && right.getKind().equals("BinaryOperation")) {
            leftReturn = visit(left, Arrays.asList("BINARY_OP"));
            rightReturn = visit(right, Arrays.asList("ASSIGNMENT", new Symbol(new Type("int", false), String.format("temporary%d", temp_sequence++))));

            String[] parts = rightReturn.split("\n");
            String aux1 = parts[parts.length - 1].split(" :=")[0];

            StringBuilder ollir = new StringBuilder();
            ollir.append(rightReturn).append("\n");

            if (variable == null) {
                ollir.append(String.format("%s %s.i32 %s", leftReturn, node.get("operation"), aux1));
            } else {
                ollir.append(String.format("%s :=.i32 %s;", OllirTemplates.variable(variable, name),
                        OllirTemplates.binary(leftReturn, aux1, node.get("operation"), new Type("int", false))));
            }
            return ollir.toString();
        }
        // Simple and Simple
        else {
            leftReturn = visit(left, Arrays.asList("BINARY_OP"));
            rightReturn = visit(right, Arrays.asList("BINARY_OP"));

            if (variable == null) {
                return String.format("%s %s.i32 %s", leftReturn, node.get("operation"), rightReturn);
            } else {
                return String.format("%s :=.i32 %s;", OllirTemplates.variable(variable, name),
                        OllirTemplates.binary(leftReturn, rightReturn, node.get("operation"), new Type("int", false)));
            }
        }
    }

    private String dealWithVariable(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return "DEFAULT_VISIT";
        visited.add(node);

        Map.Entry<Symbol, Boolean> field = null;

        if (scope.equals("CLASS")) {
            field = table.getField(node.get("name"));
        } else if (scope.equals("METHOD") && currentMethod != null) {
            field = currentMethod.getField(node.get("name"));
            if (field == null) {
                field = table.getField(node.get("name"));
            }
        }

        if (field != null) {
            String name = currentMethod.isParameter(field.getKey());
            return OllirTemplates.variable(field.getKey(), name);
        }

        return "";
    }

    private String dealWithReturn(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return "DEFAULT_VISIT";
        visited.add(node);

        String exp = visit(node.getChildren().get(0), Arrays.asList("RETURN"));

        return OllirTemplates.ret(currentMethod.getReturnType(), exp);
    }

    private String dealWithAndExpression(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return "DEFAULT_VISIT";
        visited.add(node);

        Symbol variable = (data.get(0).equals("ASSIGNMENT")) ? (Symbol) data.get(1) : null;
        String name = (variable != null) ? currentMethod.isParameter(variable) : null;

        JmmNode left = node.getChildren().get(0);
        JmmNode right = node.getChildren().get(1);

        String leftReturn;
        String rightReturn;

        // BO and BO
        if (left.getKind().equals("RelationalExpression") && right.getKind().equals("RelationalExpression")) {
            leftReturn = visit(left, Arrays.asList("ASSIGNMENT", new Symbol(new Type("boolean", false), String.format("temporary%d", temp_sequence++))));
            rightReturn = visit(right, Arrays.asList("ASSIGNMENT", new Symbol(new Type("boolean", false), String.format("temporary%d", temp_sequence++))));

            String[] parts = leftReturn.split("\n");
            String aux1 = parts[parts.length - 1].split(" :=")[0];

            String[] parts2 = leftReturn.split("\n");
            String aux2 = parts2[parts.length - 1].split(" :=")[0];

            StringBuilder ollir = new StringBuilder();
            ollir.append(leftReturn).append("\n");
            ollir.append(rightReturn).append("\n");

            if (variable == null) {
                ollir.append(String.format("%s %s.bool %s", aux1, node.get("operation"), aux2));
            } else {
                ollir.append(String.format("%s :=.bool %s;", OllirTemplates.variable(variable, name),
                        OllirTemplates.binary(aux1, aux2, node.get("operation"), new Type("boolean", false))));
            }

            return ollir.toString();
        }
        // BO and Simple
        else if (left.getKind().equals("RelationalExpression") && !right.getKind().equals("RelationalExpression")) {
            leftReturn = visit(left, Arrays.asList("ASSIGNMENT", new Symbol(new Type("boolean", false), String.format("temporary%d", temp_sequence++))));
            rightReturn = visit(right, Arrays.asList("BINARY_OP"));

            String[] parts = leftReturn.split("\n");
            String aux1 = parts[parts.length - 1].split(" :=")[0];

            StringBuilder ollir = new StringBuilder();
            ollir.append(leftReturn).append("\n");

            if (variable == null) {
                ollir.append(String.format("%s %s.bool %s", aux1, node.get("operation"), rightReturn));
            } else {
                ollir.append(String.format("%s :=.bool %s;", OllirTemplates.variable(variable, name),
                        OllirTemplates.binary(aux1, rightReturn, node.get("operation"), new Type("boolean", false))));
            }
            return ollir.toString();
        }
        // Simple and BO
        else if (!left.getKind().equals("RelationalExpression") && right.getKind().equals("RelationalExpression")) {
            leftReturn = visit(left, Arrays.asList("BINARY_OP"));
            rightReturn = visit(right, Arrays.asList("ASSIGNMENT", new Symbol(new Type("boolean", false), String.format("temporary%d", temp_sequence++))));

            String[] parts = rightReturn.split("\n");
            String aux1 = parts[parts.length - 1].split(" :=")[0];

            StringBuilder ollir = new StringBuilder();
            ollir.append(rightReturn).append("\n");

            if (variable == null) {
                ollir.append(String.format("%s %s.bool %s", leftReturn, node.get("operation"), aux1));
            } else {
                ollir.append(String.format("%s :=.bool %s;", OllirTemplates.variable(variable, name),
                        OllirTemplates.binary(leftReturn, aux1, node.get("operation"), new Type("boolean", false))));
            }
            return ollir.toString();
        }
        // Simple and Simple
        else {
            leftReturn = visit(left, Arrays.asList("BINARY_OP"));
            rightReturn = visit(right, Arrays.asList("BINARY_OP"));

            if (variable == null) {
                return String.format("%s %s.bool %s", leftReturn, node.get("operation"), rightReturn);
            } else {
                return String.format("%s :=.bool %s;", OllirTemplates.variable(variable, name),
                        OllirTemplates.binary(leftReturn, rightReturn, node.get("operation"), new Type("boolean", false)));
            }
        }
    }

    private String dealWithNotExpression(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return "DEFAULT_VISIT";
        visited.add(node);

        StringBuilder ollir = new StringBuilder();
        String expression = visit(node.getChildren().get(0), Arrays.asList("NOT"));
        String[] parts = expression.split("\n");

        String last = parts[parts.length - 1];

        Symbol variable = (data.get(0).equals("ASSIGNMENT")) ? (Symbol) data.get(1) : null;
        String name = (variable != null) ? currentMethod.isParameter(variable) : null;

        if (parts.length > 1) {
            for (int i = 0; i < parts.length - 1; i++) {
                ollir.append(parts[i]).append("\n");
            }
        }

        String[] expressionParts;
        if ((expressionParts = last.split("<")).length == 2) {
            if (variable == null) {
                ollir.append(String.format("%s>=%s", expressionParts[0], expressionParts[1]));
            } else {
                ollir.append(String.format("%s :=.bool %s>=%s;", OllirTemplates.variable(variable, name), expressionParts[0], expressionParts[1]));
            }
        } else if ((expressionParts = last.split(">=")).length == 2) {
            if (variable == null) {
                ollir.append(String.format("%s<%s", expressionParts[0], expressionParts[1]));
            } else {
                ollir.append(String.format("%s :=.bool %s<%s;", OllirTemplates.variable(variable, name), expressionParts[0], expressionParts[1]));
            }
        } else if (expression.equals("0.bool")) {
            if (variable == null) {
                ollir.append("1.bool");
            } else {
                ollir.append(String.format("%s :=.bool 1.bool;", OllirTemplates.variable(variable, name)));
            }
        } else if (expression.equals("1.bool")) {
            if (variable == null) {
                ollir.append("0.bool");
            } else {
                ollir.append(String.format("%s :=.bool 0.bool;", OllirTemplates.variable(variable, name)));
            }
        } else {
            ollir.append(String.format("%s :=.bool %s;\n", OllirTemplates.variable(new Symbol(new Type("boolean", false), "temporary" + temp_sequence)), last));
            if (variable == null) {
                ollir.append(String.format("%s !.bool %s",
                        OllirTemplates.variable(new Symbol(new Type("boolean", false), "temporary" + temp_sequence)),
                        OllirTemplates.variable(new Symbol(new Type("boolean", false), "temporary" + temp_sequence++))));
            } else {
                ollir.append(String.format("%s :=.bool %s !.bool %s;",
                        OllirTemplates.variable(variable, name),
                        OllirTemplates.variable(new Symbol(new Type("boolean", false), "temporary" + temp_sequence)),
                        OllirTemplates.variable(new Symbol(new Type("boolean", false), "temporary" + temp_sequence++))));
            }
        }

        return ollir.toString();
    }

    private String dealWithIfStatement(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return "DEFAULT_VISIT";
        visited.add(node);

        StringBuilder ollir = new StringBuilder();

        String ifCondition = visit(node.getChildren().get(0), Arrays.asList("CONDITION"));

        String[] ifConditionParts = ifCondition.split("\n");
        if (ifConditionParts.length > 1) {
            for (int i = 0; i < ifConditionParts.length - 1; i++) {
                ollir.append(ifConditionParts[i]).append("\n");
            }
        }

        // TODO - NOT expressions not done yet
        String condition;
        String[] parts;
        if ((parts = ifConditionParts[ifConditionParts.length - 1].split("<")).length == 2) {
            condition = String.format("if (%s>=%s) goto else;\n", parts[0], parts[1]);
        } else if ((parts = ifConditionParts[ifConditionParts.length - 1].split(">=")).length == 2) {
            condition = String.format("if (%s<%s) goto else;\n", parts[0], parts[1]);
        } else {
            condition = String.format("if (%s) goto else;\n", ifConditionParts[ifConditionParts.length - 1]);
        }

        ollir.append(condition);

        List<String> ifBody = new ArrayList<>();

        for (int i = 1; i < node.getChildren().size(); i++) {
            ifBody.add(visit(node.getChildren().get(i), Arrays.asList("IF")));
        }

        ollir.append(String.join("\n", ifBody));

        ollir.append("\ngoto endif;\n");

        ollir.append(visit(node.getParent().getChildren().get(1), Arrays.asList("ELSE")));
        ollir.append("\n");

        ollir.append("endif:");

        return ollir.toString();
    }

    private String dealWithElseStatement(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return "DEFAULT_VISIT";
        visited.add(node);

        StringBuilder ollir = new StringBuilder();

        ollir.append("else:").append("\n");

        List<String> elseBody = new ArrayList<>();

        for (int i = 0; i < node.getChildren().size(); i++) {
            elseBody.add(visit(node.getChildren().get(i), Arrays.asList("ELSE")));
        }

        ollir.append(String.join("\n", elseBody));

        ollir.append("\ngoto endif;");

        return ollir.toString();
    }

    private String dealWithCondition(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return "DEFAULT_VISIT";
        visited.add(node);

        return visit(node.getChildren().get(0), Arrays.asList("CONDITION"));
    }


    private String defaultVisit(JmmNode node, List<Object> data) {
        return "DEFAULT_VISIT";
    }

    private static String reduce(String nodeResult, List<String> childrenResults) {
        var content = new StringBuilder();

        if (!nodeResult.equals("DEFAULT_VISIT"))
            content.append(nodeResult);

        for (var childResult : childrenResults) {
            if (!childResult.equals("DEFAULT_VISIT"))
                content.append(String.join("\n", StringLines.getLines(childResult)));
        }

        return content.toString();
    }

}
