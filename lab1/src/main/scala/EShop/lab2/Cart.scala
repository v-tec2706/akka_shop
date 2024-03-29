package EShop.lab2

case class Cart(items: Seq[Any]) {
  def contains(item: Any): Boolean = items.contains(item)
  def addItem(item: Any): Cart =
    new Cart(item +: items)
  def removeItem(item: Any): Cart =
    new Cart(items.filter(_ != item))
  def size: Int = items.size
}

object Cart {
  def empty: Cart = new Cart(Seq.empty[Any])
}
