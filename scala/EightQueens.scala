/*
 * copyright: tsureshkumar@gmail.com
 * BSD clause 3 license
 */

object Main {
  private type SSI = Stream[Stream[Int]]
  private type Point = Pair[Int,Int]
  private def check(x:Point, y:Point) = (x,y) match { case ( (i,j), (m,n)) => j == n || (i+j) == (m+n) || (i-j) == (m-n) }
  private def safe(p:Stream[Int])(n:Int) = (for ( (i,j) <- (1 to p.length).zip(p)) yield !check( (i,j), (p.length+1, n))).foldLeft(true) (_&&_)
  def queens = queens2 { } _
  def queens2(f: => Unit)(n:Int):SSI = n match {
      case 0 => Stream[Stream[Int]](Stream())
      case m => for(p <- queens2(f)(m-1); i <- (1 to 8); if safe(p)(i))
                  yield {f;p.append(Stream[Int](i))}
    }
  def main(args: Array[String]) = {
    def print(n:Int, q:SSI) { (n,q) match {
      case (0,_) => println("")
      case (_,Stream()) => println("")
      case (m, q) => {q.head.take(8).print(); println("");print(m-1, q.tail)}
    }
                          }
    var n = 0
    print(Integer.parseInt(args(0)), queens2(n+=1)(8))
    println("found in " + n + " iterations")
  }
}
