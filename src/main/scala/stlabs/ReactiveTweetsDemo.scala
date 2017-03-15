package stlabs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, OverflowStrategy}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import scala.concurrent.ExecutionContext.Implicits.global

object ReactiveTweetsDemo extends App {

  implicit val system = ActorSystem("reactive-tweets")
  implicit val materializer = ActorMaterializer()

  final case class Author(handle: String)

  final case class Hashtag(name: String)

  final case class Tweet(author: Author, timestamp: Long, body: String) {
    def hashtags: Set[Hashtag] =
      body.split(" ").collect { case t if t.startsWith("#") => Hashtag(t) }.toSet
  }

  val akka = Hashtag("#akka")

  val tweets: Source[Tweet, NotUsed] = ???

  val authors: Source[Author, NotUsed] =
    tweets
      .filter(_.hashtags.contains(akka))
      .map(_.author)

  authors.runWith(Sink.foreach(println))

//  shorthand - runForeach
//  authors.runForeach(println)

//  used to flatten sequence
  tweets.mapConcat(_.hashtags.toList)

  val writeAuthors: Sink[Author, Unit] = ???
  val writeHashtags: Sink[Hashtag, Unit] = ???

  val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val bcast = b.add(Broadcast[Tweet](2))
    tweets ~> bcast.in
    bcast.out(0) ~> Flow[Tweet].map(_.author) ~> writeAuthors
    bcast.out(1) ~> Flow[Tweet].mapConcat(_.hashtags.toList) ~> writeHashtags

    ClosedShape
  })

  def slowComputation = ???

//  back pressure demo
  tweets
    .buffer(10, OverflowStrategy.dropHead)
    .map(slowComputation)
    .runWith(Sink.ignore)

//  materialized values
  val count: Flow[Tweet, Int, NotUsed] = Flow[Tweet].map(_ => 1)
  val sumSink = Sink.fold[Int, Int](0)(_ + _)

  val counterGraph =
    tweets
    .via(count)
    .toMat(sumSink)(Keep.right)

  val sum = counterGraph.run()
  sum.foreach(c => println(s"total tweets processed is ${c}"))


}
