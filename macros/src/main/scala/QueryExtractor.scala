package ast

import orm._
import utils.QueryUtils
import scala.reflect.macros.blackbox

class QueryExtractor [C <: blackbox.Context] (val c: C) {

  import c.universe._

  private val ops = new TreeOps[c.type](c)
  private var tableAliases: Map[String, String] = null

  private def init (tree: Tree, fromTypeName: String) = {
    tableAliases = getTableAliases(tree, fromTypeName)
  }

  def getQueryClause (tree: Tree, queryTypeName: String) = {

    init(tree, queryTypeName)

    val selectClause = getSelectClause(tree)
    val whereClause = getWhereClause(tree)
    val orderByClause = getOrderByClause(tree)
    val joinClauses = getJoinClauses(tree)
    val fromClause = getFromClause(tree, joinClauses)

    val queryClause = QueryClause (selectClause, fromClause, joinClauses, whereClause, orderByClause)
    val params = QueryClause.findParameters(queryClause)

    (queryClause, params)
  }

  private def getSelectClause (tree: Tree) = {

    val args = ops.findTypedCtorArgs(tree, "Select")
                  .flatten
                  .flatMap(getExpression)

    val distinctArgs =  ops.findTypedCtorArgs(tree, "SelectDistinct")
                           .flatten
                           .flatMap(getExpression)


    if      (distinctArgs.nonEmpty)  Some(SelectDistinctClause(args))
    else if (args.nonEmpty)          Some(SelectClause(args))
      else                           None
  }

  private def getWhereClause (tree: Tree) = {

    val args = ops.findCtorArgs(tree, "Where").flatten
                .flatMap(getExpression)

    if (args.nonEmpty)  Some(WhereClause(args))
    else                None
  }

  private def getOrderByClause (tree: Tree) = {

    val args = ops.findCtorArgs (tree, "OrderBy").flatten
                .flatMap(getExpression)

    if (args.nonEmpty)  Some(OrderByClause(args))
    else                None
  }

  private def getFromClause (tree: Tree, joinClauses: List[BaseJoinClause]) = {

    val joinTableAliases = joinClauses map (_.tableAlias)
    val fromTableAliases = tableAliases filter
      { case (tableAlias, _) => !joinTableAliases.contains(tableAlias) }

    if (tableAliases.nonEmpty)  Some(FromClause(fromTableAliases))
    else                        None
  }

  private def getJoinClauses (tree: Tree) = {

    val joinTypes = List("InnerJoin", "LeftJoin", "RightJoin")
    val argListsMap = (joinTypes zip joinTypes.map(ops.findCtorArgs(tree, _)))
                        .filter { case (_, argsLists) => argsLists.nonEmpty }
                        .toMap

    val args = for { (joinType, argLists) <- argListsMap
                      argList <- argLists.grouped(2)}
      yield (joinType, getTableAlias(argList(0).head).get, argList(1) flatMap getExpression)

    args.map { case (joinType, tableAlias, exps) => {

        val tableName = tableAliases(tableAlias)
        val joinCtor = joinType match {
                  case "InnerJoin" => InnerJoinClause
                  case "RightJoin" => RightJoinClause
                  case "LeftJoin"  => LeftJoinClause
        }

      joinCtor(tableName, tableAlias, exps)
    } }.toList
  }


  private def getExpression(tree: Tree): Option[Expression] = {

    val expressions = List (getField(tree), getOperation(tree), getLiteral(tree),
                                                getIdentity(tree))

    expressions.flatten.headOption
  }

  private def getOperation(tree: Tree) = {

    val op = tree match {
      case q"orm.QueryOps.RichAnyVal[$tpe1]($operand1).$operator($operand2)" =>
        Some((operator, List(operand1, operand2)))
      case q"orm.QueryOps.RichString($operand1).$operator($operand2)" =>
        Some((operator, List(operand1, operand2)))
      case q"orm.QueryOps.$operator[$tpe](..$operands)" =>  Some((operator, operands))
      case q"orm.QueryOps.$operator(..$operands)"       =>  Some((operator, operands))
      case q"$operand.$operator(..$operands)"           =>  Some((operator, operand +: operands))
      case _ => None
    }

    for ((operator, operands) <- op)
      yield Operation(operator.decodedName.toString, operands.flatMap(getExpression))
  }

  private def getField(tree: Tree) = tree match {

    case q"$tableAlias.$column" if (tableAliases.keySet.contains(tableAlias.toString)) =>
      Some(Field(tableAlias.toString, column.toString))
    case _ => None
  }

  private def getTableAlias(tree: Tree) = tree match {

    case Ident(tableAlias) => Some(tableAlias.toString)
    case _ => None
  }

  private def getLiteral(tree: Tree) = tree match {

    case Literal(Constant(value)) =>
      Some(LiteralVal(QueryUtils.convertLiteral(value)))
    case _ => None
  }

  private def getIdentity(tree: Tree) = {

    val identity = tree match {
      case ident @ q"$ident1.$ident2.$ident3.$ident4.$ident5" => Some(ident)
      case ident @ q"$ident1.$ident2.$ident3.$ident4" => Some(ident)
      case ident @ q"$ident1.$ident2.$ident3" => Some(ident)
      case ident @ q"$ident1.$ident2" => Some(ident)
      case Ident(ident) => Some(ident.asInstanceOf[Tree])
      case _ => None
    }

    identity map (ident => Identity(ident.toString, ident))
  }

  private def getTableAliases (tree: Tree, typeName: String) = {

    val aliases = ops.getCaseDefArgs(tree)
    val tableNames = QueryUtils.splitTupledTypeTag(typeName)

    (aliases zip tableNames).toMap
  }
}
