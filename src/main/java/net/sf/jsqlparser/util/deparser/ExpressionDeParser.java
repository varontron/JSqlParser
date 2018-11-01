/*
 * #%L
 * JSQLParser library
 * %%
 * Copyright (C) 2004 - 2013 JSQLParser
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package net.sf.jsqlparser.util.deparser;

import java.util.Iterator;
import java.util.List;
import net.sf.jsqlparser.expression.AllComparisonExpression;
import net.sf.jsqlparser.expression.AnalyticExpression;
import net.sf.jsqlparser.expression.AnyComparisonExpression;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.DateTimeLiteralExpression;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitor;
import net.sf.jsqlparser.expression.ExtractExpression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.HexValue;
import net.sf.jsqlparser.expression.IntervalExpression;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.JsonExpression;
import net.sf.jsqlparser.expression.KeepExpression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.MySQLGroupConcat;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.NumericBind;
import net.sf.jsqlparser.expression.OracleHierarchicalExpression;
import net.sf.jsqlparser.expression.OracleHint;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.RowConstructor;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimeKeyExpression;
import net.sf.jsqlparser.expression.TimeValue;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.expression.UserVariable;
import net.sf.jsqlparser.expression.ValueListExpression;
import net.sf.jsqlparser.expression.WhenClause;
import net.sf.jsqlparser.expression.WindowElement;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseAnd;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseLeftShift;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseOr;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseRightShift;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseXor;
import net.sf.jsqlparser.expression.operators.arithmetic.Concat;
import net.sf.jsqlparser.expression.operators.arithmetic.Division;
import net.sf.jsqlparser.expression.operators.arithmetic.Modulo;
import net.sf.jsqlparser.expression.operators.arithmetic.Multiplication;
import net.sf.jsqlparser.expression.operators.arithmetic.Subtraction;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.ItemsListVisitor;
import net.sf.jsqlparser.expression.operators.relational.JsonOperator;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.Matches;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.MultiExpressionList;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.expression.operators.relational.OldOracleJoinBinaryExpression;
import net.sf.jsqlparser.expression.operators.relational.RegExpMatchOperator;
import net.sf.jsqlparser.expression.operators.relational.RegExpMySQLOperator;
import net.sf.jsqlparser.expression.operators.relational.SupportsOldOracleJoinSyntax;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.SelectVisitor;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.statement.select.WithItem;

/**
 * A class to de-parse (that is, tranform from JSqlParser hierarchy into a string) an
 * {@link net.sf.jsqlparser.expression.Expression}
 */
public class ExpressionDeParser implements ExpressionVisitor, ItemsListVisitor {

    private static final String NOT = "NOT ";
    private StringBuilder builder = new StringBuilder();
    private SelectVisitor selectVisitor;
    private boolean useBracketsInExprList = true;
    private OrderByDeParser orderByDeParser = new OrderByDeParser();

    public ExpressionDeParser() {
    }

    /**
     * @param selectVisitor a SelectVisitor to de-parse SubSelects. It has to share the same<br>
     * StringBuilder as this object in order to work, as:
     *
     * <pre>
     * <code>
     * StringBuilder myBuf = new StringBuilder();
     * MySelectDeparser selectDeparser = new  MySelectDeparser();
     * selectDeparser.setBuffer(myBuf);
     * ExpressionDeParser expressionDeParser = new ExpressionDeParser(selectDeparser, myBuf);
     * </code>
     * </pre>
     *
     * @param builder the buffer that will be filled with the expression
     */
    public ExpressionDeParser(SelectVisitor selectVisitor, StringBuilder buffer) {
        this(selectVisitor, buffer, new OrderByDeParser());
    }

    ExpressionDeParser(SelectVisitor selectVisitor, StringBuilder buffer, OrderByDeParser orderByDeParser) {
        this.selectVisitor = selectVisitor;
        this.buffer = buffer;
        this.orderByDeParser = orderByDeParser;
    }

    public StringBuilder getBuffer() {
        return builder;
    }

    public void setBuffer(StringBuilder builder) {
        this.builder = builder;
    }

    @Override
    public void visit(Addition addition) {
        visitBinaryExpression(addition, " + ");
    }

    @Override
    public void visit(AndExpression andExpression) {
        visitBinaryExpression(andExpression, " AND ");
    }

    @Override
    public void visit(Between between) {
        between.getLeftExpression().accept(this);
        if (between.isNot()) {
            builder.append(" NOT");
        }

        builder.append(" BETWEEN ");
        between.getBetweenExpressionStart().accept(this);
        builder.append(" AND ");
        between.getBetweenExpressionEnd().accept(this);

    }

    @Override
    public void visit(EqualsTo equalsTo) {
        visitOldOracleJoinBinaryExpression(equalsTo, " = ");
    }

    @Override
    public void visit(Division division) {
        visitBinaryExpression(division, " / ");
    }

    @Override
    public void visit(DoubleValue doubleValue) {
        builder.append(doubleValue.toString());
    }

    @Override
    public void visit(HexValue hexValue) {
        builder.append(hexValue.toString());
    }

    @Override
    public void visit(NotExpression notExpr) {
        buffer.append(NOT);
        notExpr.getExpression().accept(this);
    }

    @Override
    public void visit(BitwiseRightShift expr) {
        visitBinaryExpression(expr, " >> ");
    }

    @Override
    public void visit(BitwiseLeftShift expr) {
        visitBinaryExpression(expr, " << ");
    }

    public void visitOldOracleJoinBinaryExpression(OldOracleJoinBinaryExpression expression, String operator) {
        if (expression.isNot()) {
            builder.append(NOT);
        }
        expression.getLeftExpression().accept(this);
        if (expression.getOldOracleJoinSyntax() == EqualsTo.ORACLE_JOIN_RIGHT) {
            builder.append("(+)");
        }
        builder.append(operator);
        expression.getRightExpression().accept(this);
        if (expression.getOldOracleJoinSyntax() == EqualsTo.ORACLE_JOIN_LEFT) {
            builder.append("(+)");
        }
    }

    @Override
    public void visit(GreaterThan greaterThan) {
        visitOldOracleJoinBinaryExpression(greaterThan, " > ");
    }

    @Override
    public void visit(GreaterThanEquals greaterThanEquals) {
        visitOldOracleJoinBinaryExpression(greaterThanEquals, " >= ");

    }

    @Override
    public void visit(InExpression inExpression) {
        if (inExpression.getLeftExpression() == null) {
            inExpression.getLeftItemsList().accept(this);
        } else {
            inExpression.getLeftExpression().accept(this);
            if (inExpression.getOldOracleJoinSyntax() == SupportsOldOracleJoinSyntax.ORACLE_JOIN_RIGHT) {
                builder.append("(+)");
            }
        }
        if (inExpression.isNot()) {
            builder.append(" NOT");
        }
        builder.append(" IN ");

        inExpression.getRightItemsList().accept(this);
    }

    @Override
    public void visit(SignedExpression signedExpression) {
        builder.append(signedExpression.getSign());
        signedExpression.getExpression().accept(this);
    }

    @Override
    public void visit(IsNullExpression isNullExpression) {
        isNullExpression.getLeftExpression().accept(this);
        if (isNullExpression.isUseIsNull()) {
            if (isNullExpression.isNot()) {
                buffer.append(" NOT ISNULL");
            } else {
                buffer.append(" ISNULL");
            }
        } else {
            if (isNullExpression.isNot()) {
                buffer.append(" IS NOT NULL");
            } else {
                buffer.append(" IS NULL");
            }
        }
    }

    @Override
    public void visit(JdbcParameter jdbcParameter) {
        buffer.append("?");
        if (jdbcParameter.isUseFixedIndex()) {
            buffer.append(jdbcParameter.getIndex());
        }

    }

    @Override
    public void visit(LikeExpression likeExpression) {
        visitBinaryExpression(likeExpression, likeExpression.isCaseInsensitive() ? " ILIKE " : " LIKE ");
        String escape = likeExpression.getEscape();
        if (escape != null) {
            builder.append(" ESCAPE '").append(escape).append('\'');
        }
    }

    @Override
    public void visit(ExistsExpression existsExpression) {
        if (existsExpression.isNot()) {
            builder.append("NOT EXISTS ");
        } else {
            builder.append("EXISTS ");
        }
        existsExpression.getRightExpression().accept(this);
    }

    @Override
    public void visit(LongValue longValue) {
        builder.append(longValue.getStringValue());

    }

    @Override
    public void visit(MinorThan minorThan) {
        visitOldOracleJoinBinaryExpression(minorThan, " < ");

    }

    @Override
    public void visit(MinorThanEquals minorThanEquals) {
        visitOldOracleJoinBinaryExpression(minorThanEquals, " <= ");

    }

    @Override
    public void visit(Multiplication multiplication) {
        visitBinaryExpression(multiplication, " * ");

    }

    @Override
    public void visit(NotEqualsTo notEqualsTo) {
        visitOldOracleJoinBinaryExpression(notEqualsTo, " " + notEqualsTo.getStringExpression() + " ");

    }

    @Override
    public void visit(NullValue nullValue) {
        builder.append(nullValue.toString());

    }

    @Override
    public void visit(OrExpression orExpression) {
        visitBinaryExpression(orExpression, " OR ");

    }

    @Override
    public void visit(Parenthesis parenthesis) {
        if (parenthesis.isNot()) {
            builder.append(NOT);
        }

        builder.append("(");
        parenthesis.getExpression().accept(this);
        builder.append(")");

    }

    @Override
    public void visit(StringValue stringValue) {
        if (stringValue.getPrefix() != null) {
            buffer.append(stringValue.getPrefix());
        }
        buffer.append("'").append(stringValue.getValue()).append("'");

    }

    @Override
    public void visit(Subtraction subtraction) {
        visitBinaryExpression(subtraction, " - ");
    }

    private void visitBinaryExpression(BinaryExpression binaryExpression, String operator) {
        if (binaryExpression.isNot()) {
            builder.append(NOT);
        }
        binaryExpression.getLeftExpression().accept(this);
        builder.append(operator);
        binaryExpression.getRightExpression().accept(this);

    }

    @Override
    public void visit(SubSelect subSelect) {
        if (subSelect.isUseBrackets()) {
            buffer.append("(");
        }
        if (selectVisitor != null) {
            if (subSelect.getWithItemsList() != null) {
                buffer.append("WITH ");
                for (Iterator<WithItem> iter = subSelect.getWithItemsList().iterator(); iter.
                        hasNext();) {
                    iter.next().accept(selectVisitor);
                    if (iter.hasNext()) {
                        buffer.append(", ");
                    }
                    buffer.append(" ");
                }
                buffer.append(" ");
            }

            subSelect.getSelectBody().accept(selectVisitor);
        }
        if (subSelect.isUseBrackets()) {
            buffer.append(")");
        }
    }

    @Override
    public void visit(Column tableColumn) {
        final Table table = tableColumn.getTable();
        String tableName = null;
        if (table != null) {
            if (table.getAlias() != null) {
                tableName = table.getAlias().getName();
            } else {
                tableName = table.getFullyQualifiedName();
            }
        }
        if (tableName != null && !tableName.isEmpty()) {
            builder.append(tableName).append(".");
        }

        builder.append(tableColumn.getColumnName());
    }

    @Override
    public void visit(Function function) {
        if (function.isEscaped()) {
            builder.append("{fn ");
        }

        builder.append(function.getName());
        if (function.isAllColumns() && function.getParameters() == null) {
            builder.append("(*)");
        } else if (function.getParameters() == null) {
            builder.append("()");
        } else {
            boolean oldUseBracketsInExprList = useBracketsInExprList;
            if (function.isDistinct()) {
                useBracketsInExprList = false;
                builder.append("(DISTINCT ");
            } else if (function.isAllColumns()) {
                useBracketsInExprList = false;
                builder.append("(ALL ");
            }
            visit(function.getParameters());
            useBracketsInExprList = oldUseBracketsInExprList;
            if (function.isDistinct() || function.isAllColumns()) {
                builder.append(")");
            }
        }

        if (function.getAttribute() != null) {
            builder.append(".").append(function.getAttribute());
        }
        if (function.getKeep() != null) {
            builder.append(" ").append(function.getKeep());
        }

        if (function.isEscaped()) {
            builder.append("}");
        }
    }

    @Override
    public void visit(ExpressionList expressionList) {
        if (useBracketsInExprList) {
            builder.append("(");
        }
        for (Iterator<Expression> iter = expressionList.getExpressions().iterator(); iter.hasNext();) {
            Expression expression = iter.next();
            expression.accept(this);
            if (iter.hasNext()) {
                builder.append(", ");
            }
        }
        if (useBracketsInExprList) {
            builder.append(")");
        }
    }

    public SelectVisitor getSelectVisitor() {
        return selectVisitor;
    }

    public void setSelectVisitor(SelectVisitor visitor) {
        selectVisitor = visitor;
    }

    @Override
    public void visit(DateValue dateValue) {
        builder.append("{d '").append(dateValue.getValue().toString()).append("'}");
    }

    @Override
    public void visit(TimestampValue timestampValue) {
        builder.append("{ts '").append(timestampValue.getValue().toString()).append("'}");
    }

    @Override
    public void visit(TimeValue timeValue) {
        builder.append("{t '").append(timeValue.getValue().toString()).append("'}");
    }

    @Override
    public void visit(CaseExpression caseExpression) {
        builder.append("CASE ");
        Expression switchExp = caseExpression.getSwitchExpression();
        if (switchExp != null) {
            switchExp.accept(this);
            builder.append(" ");
        }

        for (Expression exp : caseExpression.getWhenClauses()) {
            exp.accept(this);
        }

        Expression elseExp = caseExpression.getElseExpression();
        if (elseExp != null) {
            builder.append("ELSE ");
            elseExp.accept(this);
            builder.append(" ");
        }

        builder.append("END");
    }

    @Override
    public void visit(WhenClause whenClause) {
        builder.append("WHEN ");
        whenClause.getWhenExpression().accept(this);
        builder.append(" THEN ");
        whenClause.getThenExpression().accept(this);
        builder.append(" ");
    }

    @Override
    public void visit(AllComparisonExpression allComparisonExpression) {
        builder.append("ALL ");
        allComparisonExpression.getSubSelect().accept((ExpressionVisitor) this);
    }

    @Override
    public void visit(AnyComparisonExpression anyComparisonExpression) {
        builder.append(anyComparisonExpression.getAnyType().name()).append(" ");
        anyComparisonExpression.getSubSelect().accept((ExpressionVisitor) this);
    }

    @Override
    public void visit(Concat concat) {
        visitBinaryExpression(concat, " || ");
    }

    @Override
    public void visit(Matches matches) {
        visitOldOracleJoinBinaryExpression(matches, " @@ ");
    }

    @Override
    public void visit(BitwiseAnd bitwiseAnd) {
        visitBinaryExpression(bitwiseAnd, " & ");
    }

    @Override
    public void visit(BitwiseOr bitwiseOr) {
        visitBinaryExpression(bitwiseOr, " | ");
    }

    @Override
    public void visit(BitwiseXor bitwiseXor) {
        visitBinaryExpression(bitwiseXor, " ^ ");
    }

    @Override
    public void visit(CastExpression cast) {
        if (cast.isUseCastKeyword()) {
            builder.append("CAST(");
            builder.append(cast.getLeftExpression());
            builder.append(" AS ");
            builder.append(cast.getType());
            builder.append(")");
        } else {
            builder.append(cast.getLeftExpression());
            builder.append("::");
            builder.append(cast.getType());
        }
    }

    @Override
    public void visit(Modulo modulo) {
        visitBinaryExpression(modulo, " % ");
    }

    @Override
    public void visit(AnalyticExpression aexpr) {
        String name = aexpr.getName();
        Expression expression = aexpr.getExpression();
        Expression offset = aexpr.getOffset();
        Expression defaultValue = aexpr.getDefaultValue();
        boolean isAllColumns = aexpr.isAllColumns();
        KeepExpression keep = aexpr.getKeep();
        ExpressionList partitionExpressionList = aexpr.getPartitionExpressionList();
        List<OrderByElement> orderByElements = aexpr.getOrderByElements();
        WindowElement windowElement = aexpr.getWindowElement();

        buffer.append(name).append("(");
        if (aexpr.isDistinct()) {
            buffer.append("DISTINCT ");
        }
        if (expression != null) {
            expression.accept(this);
            if (offset != null) {
                buffer.append(", ");
                offset.accept(this);
                if (defaultValue != null) {
                    buffer.append(", ");
                    defaultValue.accept(this);
                }
            }
        } else if (isAllColumns) {
            buffer.append("*");
        }
        if (aexpr.isIgnoreNulls()) {
            buffer.append(" IGNORE NULLS");
        }
        buffer.append(") ");
        if (keep != null) {
            keep.accept(this);
            buffer.append(" ");
        }

        switch (aexpr.getType()) {
            case WITHIN_GROUP:
                buffer.append("WITHIN GROUP");
                break;
            default:
                buffer.append("OVER");
        }
        buffer.append(" (");

        if (partitionExpressionList != null && !partitionExpressionList.getExpressions().isEmpty()) {
            buffer.append("PARTITION BY ");
            List<Expression> expressions = partitionExpressionList.getExpressions();
            for (int i = 0; i < expressions.size(); i++) {
                if (i > 0) {
                    buffer.append(", ");
                }
                expressions.get(i).accept(this);
            }
            buffer.append(" ");
        }
        if (orderByElements != null && !orderByElements.isEmpty()) {
            buffer.append("ORDER BY ");
            orderByDeParser.setExpressionVisitor(this);
            orderByDeParser.setBuffer(buffer);
            for (int i = 0; i < orderByElements.size(); i++) {
                if (i > 0) {
                    buffer.append(", ");
                }
                orderByDeParser.deParseElement(orderByElements.get(i));
            }

            if (windowElement != null) {
                buffer.append(' ');
                buffer.append(windowElement);
            }
        }

        buffer.append(")");
    }

    @Override
    public void visit(ExtractExpression eexpr) {
        builder.append("EXTRACT(").append(eexpr.getName());
        builder.append(" FROM ");
        eexpr.getExpression().accept(this);
        builder.append(')');
    }

    @Override
    public void visit(MultiExpressionList multiExprList) {
        for (Iterator<ExpressionList> it = multiExprList.getExprList().iterator(); it.hasNext();) {
            it.next().accept(this);
            if (it.hasNext()) {
                builder.append(", ");
            }
        }
    }

    @Override
    public void visit(IntervalExpression iexpr) {
        builder.append(iexpr.toString());
    }

    @Override
    public void visit(JdbcNamedParameter jdbcNamedParameter) {
        builder.append(jdbcNamedParameter.toString());
    }

    @Override
    public void visit(OracleHierarchicalExpression oexpr) {
        builder.append(oexpr.toString());
    }

    @Override
    public void visit(RegExpMatchOperator rexpr) {
        visitBinaryExpression(rexpr, " " + rexpr.getStringExpression() + " ");
    }

    @Override
    public void visit(RegExpMySQLOperator rexpr) {
        visitBinaryExpression(rexpr, " " + rexpr.getStringExpression() + " ");
    }

    @Override
    public void visit(JsonExpression jsonExpr) {
        builder.append(jsonExpr.toString());
    }

    @Override
    public void visit(JsonOperator jsonExpr) {
        visitBinaryExpression(jsonExpr, " " + jsonExpr.getStringExpression() + " ");
    }

    @Override
    public void visit(UserVariable var) {
        builder.append(var.toString());
    }

    @Override
    public void visit(NumericBind bind) {
        builder.append(bind.toString());
    }

    @Override
    public void visit(KeepExpression aexpr) {
        builder.append(aexpr.toString());
    }

    @Override
    public void visit(MySQLGroupConcat groupConcat) {
        builder.append(groupConcat.toString());
    }

    @Override
    public void visit(ValueListExpression valueList) {
        buffer.append(valueList.toString());
    }

    @Override
    public void visit(RowConstructor rowConstructor) {
        if (rowConstructor.getName() != null) {
            builder.append(rowConstructor.getName());
        }
        builder.append("(");
        boolean first = true;
        for (Expression expr : rowConstructor.getExprList().getExpressions()) {
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            expr.accept(this);
        }
        builder.append(")");
    }

    @Override
    public void visit(OracleHint hint) {
        builder.append(hint.toString());
    }

    @Override
    public void visit(TimeKeyExpression timeKeyExpression) {
        builder.append(timeKeyExpression.toString());
    }

    @Override
    public void visit(DateTimeLiteralExpression literal) {
        builder.append(literal.toString());
    }

    @Override
    public void visit(YADAMarkupParameter yadaMarkupParameter) {
      builder.append("?");
      if (yadaMarkupParameter.getType() != null) {
          builder.append(yadaMarkupParameter.getType());
      }
    }

}
