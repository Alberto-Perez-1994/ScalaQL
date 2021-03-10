package orm


sealed trait SelectBase [T]
case class Select [T <: DbDataSet] (select: T) extends SelectBase[T]
case class SelectDistinct [T <: DbDataSet] (select: T) extends SelectBase[T]
case class Where (where: Boolean*)
case class OrderBy  (orderBy: Any*)

sealed trait BaseJoin
case class InnerJoin (table: DbDataSet) (join: Boolean*) extends BaseJoin
case class LeftJoin (table: DbDataSet) (join: Boolean*) extends BaseJoin
case class RightJoin (table: DbDataSet) (join: Boolean*) extends BaseJoin


class Query[T <: DbDataSet] {

  def where (filterFns: Seq[T ⇒ Boolean]) = ???
}

object Query {

  def apply[T <: DbDataSet] (select: SelectBase[T]) = new Query[T]

  def apply[T <: DbDataSet] (select: SelectBase[T],
                where: Where) = new Query[T]

  def apply[T <: DbDataSet] (select: SelectBase[T],
                orderBy: OrderBy) = new Query[T]

  def apply[T <: DbDataSet] (select: SelectBase[T],
                where: Where,
                orderBy: OrderBy) = new Query[T]

  def apply[T <: DbDataSet] (select: SelectBase[T],
                joins: BaseJoin*) = new Query[T]

  def apply[T <: DbDataSet] (select: SelectBase[T],
                where: Where,
                joins: BaseJoin*) = new Query[T]

  def apply[T <: DbDataSet] (select: SelectBase[T],
                orderBy: OrderBy,
                joins: BaseJoin*) = new Query[T]

  def apply[T <: DbDataSet] (select: SelectBase[T],
                where: Where,
                orderBy: OrderBy,
                joins: BaseJoin*) = new Query[T]
}
