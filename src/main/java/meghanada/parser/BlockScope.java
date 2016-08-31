package meghanada.parser;

import com.github.javaparser.Range;
import com.google.common.base.MoreObjects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

import java.util.*;

public class BlockScope extends Scope {

    private static Logger log = LogManager.getLogger(BlockScope.class);
    List<BlockScope> innerScopes = new ArrayList<>(8);
    Deque<BlockScope> currentScope = new ArrayDeque<>(8);
    boolean isLambdaBlock;
    private List<ExpressionScope> expressions = new ArrayList<>(8);
    private Deque<ExpressionScope> currentExpr = new ArrayDeque<>(8);
    private BlockScope parent;

    BlockScope(final String name, final Range range) {
        super(name, range);
    }

    private BlockScope(final String name, final Range range, Map<String, Variable> classSymbol) {
        super(name, range);
        for (Map.Entry<String, Variable> entry : classSymbol.entrySet()) {
            this.nameSymbols.add(entry.getValue());
        }
    }

    void startBlock(final String name, final Range range, final Map<String, Variable> fieldSymbol) {
        BlockScope blockScope = new BlockScope(name, range, fieldSymbol);
        this.startBlock(blockScope);
    }

    void startBlock(final String name, final Range range, final Map<String, Variable> fieldSymbol, final boolean isLambdaBlock) {
        BlockScope blockScope = new BlockScope(name, range, fieldSymbol);
        blockScope.isLambdaBlock = isLambdaBlock;
        this.startBlock(blockScope);
    }

    void startBlock(BlockScope blockScope) {
        blockScope.parent = this;
        if (this.isLambdaBlock) {
            blockScope.isLambdaBlock = true;
        }
        this.currentScope.push(blockScope);
    }

    BlockScope currentBlock() {
        if (this.currentScope == null) {
            return null;
        }
        return this.currentScope.peek();
    }

    BlockScope endBlock() {
        if (this.innerScopes == null) {
            return null;
        }

        this.innerScopes.add(this.currentBlock());
        return this.currentScope.remove();
    }

    BlockScope getParent() {
        return parent;
    }

    public List<BlockScope> getInnerScopes() {
        return innerScopes;
    }

    protected Optional<ExpressionScope> getExpression(final int line) {
        log.traceEntry("line={} expressions={}", line, this.expressions);
        final Optional<ExpressionScope> result = this.expressions.stream().filter(expr -> expr.contains(line)).findFirst();
        return log.traceExit(result);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("scope", name)
                .add("beginLine", getBeginLine())
                .add("endLine", getEndLine())
                .add("parent", parent.getName())
                .add("isLambdaBlock", isLambdaBlock)
                .toString();
    }

    @Override
    void addNameSymbol(final Variable var) {
        final Optional<ExpressionScope> currentExpr = this.getCurrentExpr();
        if (currentExpr.isPresent()) {
            currentExpr.get().addNameSymbol(var);
        } else {
            super.addNameSymbol(var);
        }
    }

    @Override
    MethodCallSymbol addMethodCall(MethodCallSymbol mcs) {
        final Optional<ExpressionScope> currentExpr = this.getCurrentExpr();
        if (currentExpr.isPresent()) {
            currentExpr.get().addMethodCall(mcs);
        } else {
            super.addMethodCall(mcs);
        }
        return mcs;
    }

    @Override
    FieldAccessSymbol addFieldAccess(FieldAccessSymbol fas) {
        final Optional<ExpressionScope> currentExpr = this.getCurrentExpr();
        if (currentExpr.isPresent()) {
            currentExpr.get().addFieldAccess(fas);
        } else {
            super.addFieldAccess(fas);
        }
        return fas;
    }

    @Override
    Set<Variable> getNameSymbol(final int line) {
        if (this.contains(line)) {
            final Optional<ExpressionScope> expressionOpt = this.getExpression(line);
            if (expressionOpt.isPresent()) {
                return expressionOpt.get().getNameSymbols();
            }
            return this.getNameSymbols();
        }
        return Collections.emptySet();
    }

    @Override
    Map<String, Variable> getDeclaratorMap() {
        Map<String, Variable> result = new HashMap<>(32);
        this.nameSymbols
                .stream()
                .filter(Variable::isDeclaration)
                .forEach(v -> result.putIfAbsent(v.name, v));
        this.expressions
                .stream()
                .flatMap(expressionScope -> expressionScope.getNameSymbols().stream())
                .filter(Variable::isDeclaration)
                .forEach(v -> result.putIfAbsent(v.name, v));
        return result;
    }

    @Override
    Map<String, Variable> getDeclaratorMap(int line) {
        Map<String, Variable> result = new HashMap<>(32);
        this.getNameSymbol(line)
                .stream()
                .filter(Variable::isDeclaration)
                .forEach(v -> result.putIfAbsent(v.name, v));
        this.expressions
                .stream()
                .filter(expr -> expr.contains(line))
                .forEach(expr -> expr.getNameSymbol(line)
                        .stream()
                        .filter(Variable::isDeclaration)
                        .forEach(v -> result.putIfAbsent(v.name, v)));
        return result;
    }

    @Override
    List<MethodCallSymbol> getMethodCallSymbols(final int line) {
        log.traceEntry("line={}", line);

        final List<MethodCallSymbol> result = new ArrayList<>(32);
        super.getMethodCallSymbols(line)
                .forEach(result::add);

        this.expressions
                .stream()
                .filter(expr -> expr.contains(line))
                .map(expr -> expr.getMethodCallSymbols(line))
                .forEach(mcs -> mcs.forEach(result::add));

        return log.traceExit(result);
    }

    @Override
    List<FieldAccessSymbol> getFieldAccessSymbols(final int line) {
        log.traceEntry("line={}", line);

        final List<FieldAccessSymbol> result = new ArrayList<>(32);
        super.getFieldAccessSymbols(line)
                .forEach(result::add);

        this.expressions
                .stream()
                .filter(expr -> expr.contains(line))
                .map(expr -> expr.getFieldAccessSymbols(line))
                .forEach(fas -> fas.forEach(result::add));

        return log.traceExit(result);
    }

    void startExpression(final ExpressionScope expr) {
        this.currentExpr.push(expr);
    }

    private Optional<ExpressionScope> getCurrentExpr() {
        return Optional.ofNullable(this.currentExpr.peek());
    }

    void endExpression() {
        final EntryMessage entryMessage = log.traceEntry("currentExpr={}", currentExpr);
        this.getCurrentExpr().ifPresent(expr -> {
            this.expressions.add(expr);
            this.currentExpr.remove();
        });
        log.traceExit(entryMessage);
    }

}
