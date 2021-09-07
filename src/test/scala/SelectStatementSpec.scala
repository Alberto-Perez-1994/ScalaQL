package test

import com.albertoperez1994.scalaql._
import com.albertoperez1994.scalaql.utils.StringUtils._
import org.scalatest.funspec.AnyFunSpec
import TestModels._

class SelectStatementSpec extends AnyFunSpec {

  describe("A Select Statement") {

  import ResultModels._


    it("can serialize simple queries") {

      val simpleQuery = queryAll[Person]

      val simpleQuerySql = """
        SELECT      p.*
        FROM        [Person] AS p"""

      assert(simpleQuery.sql.normalized() == simpleQuerySql.normalized())
    }


    it("can serialize complex queries") {

      val complexQuery = query[(Person, Address, Telephone)].select {
        case (p, a, t) => Query(
          Select          (Result (p.name, p.age, a.street, 4)),
          Where           (a.street like "%Baker St%",
                            coalesce (p.isEmployer, false)),
          OrderBy         (desc (p.age)),
          InnerJoin (t)   (t.id === p.telephoneId),
          LeftJoin  (a)   (a.id === p.addressId))
      }

      val complexQuerySql = """
          SELECT      p.[name], p.[age], a.[street], 4
          FROM        [Person] AS p
          INNER JOIN  [Telephone] AS t ON t.[id] = p.[telephoneId]
          LEFT JOIN   [Address] AS a ON a.[id] = p.[addressId]
          WHERE       (a.[street] like '%Baker St%') and
                      (coalesce (p.[isEmployer], 0))
          ORDER BY    p.[age] desc"""

      assert(complexQuery.sql.normalized() == complexQuerySql.normalized())
    }


    it("can serialize queries including literal values") {

      val literalValsQuery = query[Address].select {
        case a => Query(
          SelectAll (a),
          Where (a.street in List("Carnaby St", "Downing St"))
        )
      }

      val literalValsQuerySql = """
          SELECT      a.*
          FROM        [Address] AS a
          WHERE       a.[street] in ('Carnaby St', 'Downing St')"""

      assert(literalValsQuery.sql.normalized() == literalValsQuerySql.normalized())
    }

    it("can serialize queries including runtime values") {

      val allowedPhoneNumbers = List(658976534L, 870127465L)

      val runtimeValsQuery = query[Telephone].select {
        case t => Query(
          SelectAll (t),
          Where (t.number in allowedPhoneNumbers)
        )
      }

      val runtimeValsQuerySql = """
          SELECT      t.*
          FROM        [Telephone] AS t
          WHERE       t.[number] in (?, ?)"""


      assert(runtimeValsQuery.sql.normalized() == runtimeValsQuerySql.normalized())

      assert(runtimeValsQuery.paramList == allowedPhoneNumbers)
    }


    it("can serialize queries using set operations") {

      val personsQuery1 = query[Person].select {
        p => Query(
          SelectDistinctAll (p),
          Where (p.age < 38))
      }

      val personsQuery2 = query[Person].select {
        p => Query(
          SelectDistinctAll (p),
          Where (p.name <> "George"))
      }

      val unionQuerySql =
        """SELECT      DISTINCT p.*
           FROM        [Person] AS p
           WHERE       p.[age] < 38

           UNION

           SELECT      DISTINCT p.*
           FROM        [Person] AS p
           WHERE       p.[name] <> 'George'"""

      val intersectQuerySql =
       """SELECT      DISTINCT p.*
          FROM        [Person] AS p
          WHERE       p.[age] < 38

          INTERSECT

          SELECT      DISTINCT p.*
          FROM        [Person] AS p
          WHERE       p.[name] <> 'George'"""


      assert((personsQuery1 union personsQuery2).sql.normalized() == unionQuerySql.normalized())

      assert((personsQuery1 intersect personsQuery2).sql.normalized() == intersectQuerySql.normalized())
    }


    it("can serialize deeply nested queries into nested CTEs") {

      val personsQuery = query[Person].select {
        p => Query(
          SelectAll (p),
          Where (p.name === "Mark" or p.name === "John",
                 p.age > 25))
      }

      val adressesQuery = query[Address].select {
        a => Query(
          SelectAll (a),
          Where (a.street like "%Baker St%"))
      }

      val telephoneQuery = query[Telephone].select {
        t => Query(
          SelectAll (t),
          Where (t.number <> 676874981))
      }

      val innerNestedQuery1 = query(personsQuery, adressesQuery).select {
        case (p, a) => Query(
          Select        (InnerResult1(p.name, max(p.age), a.street))
        )
      }

      val innerNestedQuery2 = query(personsQuery, telephoneQuery).select {
        case (p, t) => Query(
          Select        (InnerResult2(p.name, t.number)),
        )
      }

      val deeplyNestedQuery = query(innerNestedQuery1, innerNestedQuery2).select {
        case (a, b) => Query(
          Select          (Result (a.name, a.age, a.street, b.telephoneNumber)),
          OrderBy         (asc (a.name)),
          InnerJoin (b)    (b.name === a.name))
      }

      val deeplyNestedQuerySql =
       """WITH q1 AS
          (
            SELECT      p.*
            FROM        [Person] AS p
            WHERE       (p.[name] = 'Mark' or p.[name] = 'John') and
                        (p.[age] > 25)
          ),
          q2 AS
          (
            SELECT      a.*
            FROM        [Address] AS a
            WHERE       a.[street] like '%Baker St%'
          ),
          q3 AS
          (
            SELECT      p.[name], max (p.[age]), a.[street]
            FROM        [q1] AS p, [q2] AS a
            GROUP BY    p.[name], a.[street]
          ),
          q4 AS
          (
            SELECT      p.*
            FROM        [Person] AS p
            WHERE       (p.[name] = 'Mark' or p.[name] = 'John') and
                        (p.[age] > 25)
          ),
          q5 AS
          (
            SELECT      t.*
            FROM        [Telephone] AS t
            WHERE       t.[number] <> 676874981
          ),
          q6 AS
          (
            SELECT      p.[name], t.[number]
            FROM        [q4] AS p, [q5] AS t
          )

          SELECT      a.[name], a.[age], a.[street], b.[telephoneNumber]
          FROM        [q3] AS a
          INNER JOIN  [q6] AS b ON b.[name] = a.[name]
          ORDER BY    a.[name] asc"""


      assert(deeplyNestedQuery.sql.normalized() == deeplyNestedQuerySql.normalized())
    }
  }

  object ResultModels {

    case class Result (name: String, age: Int, street: String, telephoneNumber: Long) extends DbResult
    case class InnerResult1 (name: String, age: Int, street: String) extends DbResult
    case class InnerResult2 (name: String, telephoneNumber: Long) extends DbResult
  }
}
