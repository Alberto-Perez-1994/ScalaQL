package it

import com.albertoperez1994.scalaql.DbTable

object TestModels {

  case class Person (name: String, age: Int, isEmployer: Boolean, addressId: Int, telephoneId: Int)
                      extends DbTable

  case class Address (id: Int, street: String) extends DbTable
  case class Telephone (id: Int, number: Long) extends DbTable
}
