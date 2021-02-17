package ast

import orm._
import scala.reflect.macros.blackbox
import scala.reflect.api.Liftables
import scala.reflect.api.StandardLiftables

class QueryImpl(val c: blackbox.Context) {

  import c.universe._

  def select[T: WeakTypeTag, R: WeakTypeTag] (queryFnTree: Tree) =
    buildFullQuery[T, R] (queryFnTree)

  def selectDebug[T: WeakTypeTag, R: WeakTypeTag] (queryFnTree: Tree) =
    buildFullQuery[T, R] (queryFnTree, debug = true)


  private val extractor = new QueryExtractor[c.type](c)

  private def buildFullQuery[T: WeakTypeTag, R: WeakTypeTag] (queryFnTree: Tree, debug: Boolean = false) = {

    val (queryClause, params) = extractor.getQueryClause(queryFnTree, weakTypeOf[T].toString)

    if (debug) {
      throw new Exception(s""" |  Debugging query: \n\n\n${queryClause.sql}\n\n
                               |Query Tree: \n\n ${queryClause}\n\n""".stripMargin)
    }

    c.Expr[FullQuery[T, R]](q"""FullQuery(queryClauseSql = Some(${queryClause.sql}),
                                          params = ${params.asInstanceOf[Map[String, Tree]]})""")
  }
}