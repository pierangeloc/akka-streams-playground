import akka.stream
import akka.stream.{OverflowStrategy, UniformFanInShape}
import akka.stream.scaladsl._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object Graphs extends App with BaseStreamingFacilities {

    //  like connecting circuits
  val pickMaxOfThree = FlowGraph.partial() { implicit b =>
    import FlowGraph.Implicits._
    val zip1  = b add ZipWith[Int, Int, Int](math.max  _)
    val zip2  = b add ZipWith[Int, Int, Int](math.max  _)

    zip1.out ~> zip2.in0

    UniformFanInShape(zip2.out, zip1.in0, zip1.in1, zip2.in1)

  }

  val resultSink = Sink.head[Int]

  //let's close the graph connecting sources and sinks
  val g = FlowGraph.closed(resultSink) {implicit builder =>
    resultSink =>
    import FlowGraph.Implicits._

    val pickMax = builder.add(pickMaxOfThree)

    //connect the in/outlets
    Source.single(1) ~> pickMax.in(0)
    Source.single(2) ~> pickMax.in(1)
    Source.single(3) ~> pickMax.in(2)

    pickMax.out ~> resultSink.inlet
  }


  val max: Future[Int] = g.run()
  println(Await.result(max, 1 second))

  println("preparing cyclic graphs")

  val cyclic = FlowGraph.closed() { implicit builder =>
    import FlowGraph.Implicits._

    val merge = builder.add(Merge[Int](2))
    val broadcast = builder.add(Broadcast[Int](2))
    //with this unbounded source, the elements in the ring are always increasing, filling all buffers. Result: Ring stuck
    val source = Source(() => Iterator.from(0))

    //with this bounded source, we introduce 1 element in the ring and this keeps flowing around, and buffers are emptied and refilled nicely
    //    val source = Source.single(1)
//    val source = Source(1 to 5).mapAsync(1)((i: Int) => akka.pattern.after(1 seconds, actorSystem.scheduler)(Future.successful(i)))
    //this is a graph with cycles. It might induce an infinite flow as the generated values are returning back through the feedback ring
    source ~> merge ~> Flow[Int].map {s => println(s); s} ~>  broadcast ~>Sink.ignore
              merge             <~                            broadcast
  }


  val cyclicUnfair = FlowGraph.closed() { implicit builder =>
    import FlowGraph.Implicits._

    //1 preferred input, 1 secondary input
    val merge = builder.add(MergePreferred[Int](1))
    val broadcast = builder.add(Broadcast[Int](2))
    //with this unbounded source, the elements in the ring are always increasing, filling all buffers. Result: Ring stuck
    val source = Source(() => Iterator.from(0))

    //with this bounded source, we introduce 1 element in the ring and this keeps flowing around, and buffers are emptied and refilled nicely
    //    val source = Source.single(1)
//        val source = Source(1 to 5).mapAsync(1)((i: Int) => akka.pattern.after(1 seconds, actorSystem.scheduler)(Future.successful(i)))
    //this is a graph with cycles. It might induce an infinite flow as the generated values are returning back through the feedback ring
    source ~> merge  ~> Flow[Int].map {s => println(s); s} ~>  broadcast ~>Sink.ignore
              merge.preferred             <~                            broadcast
  }



  val cyclicFairDropping = FlowGraph.closed() { implicit builder =>
    import FlowGraph.Implicits._

    //1 preferred input, 1 secondary input
    val merge = builder.add(MergePreferred[Int](1))
    val broadcast = builder.add(Broadcast[Int](2))
    //with this unbounded source, the elements in the ring are always increasing, filling all buffers. Result: Ring stuck
    val source = Source(() => Iterator.from(0))

    //with this bounded source, we introduce 1 element in the ring and this keeps flowing around, and buffers are emptied and refilled nicely
    //    val source = Source.single(1)
    //        val source = Source(1 to 5).mapAsync(1)((i: Int) => akka.pattern.after(1 seconds, actorSystem.scheduler)(Future.successful(i)))
    //this is a graph with cycles. It might induce an infinite flow as the generated values are returning back through the feedback ring
    source ~> merge             ~> Flow[Int].map {s => println(s); s}                         ~>  broadcast ~>Sink.ignore
              merge.preferred   <~ Flow[Int].buffer(10, OverflowStrategy.dropHead)            <~  broadcast
  }


  //N.B. We can also see how the runnable cycles are running in parallel, even if one deadlocks

  val cyclicWithZip = FlowGraph.closed() { implicit builder =>
    import FlowGraph.Implicits._

    //1 preferred input, 1 secondary input
    val zip = builder.add(ZipWith((l: Int, r: Int) => l))
    val broadcast = builder.add(Broadcast[Int](2))
    //with this unbounded source, the elements in the ring are always increasing, filling all buffers. Result: Ring stuck
    val source = Source(() => Iterator.from(0))
    val start = Source.single(0)
    //concat concats all elements from one stream to the ones from the second one, playing them all in sequence
    val concat = builder.add(Concat[Int]())

    //this gets stuck because there is no output feeding the zip.in1 as it is provided by the out. but the out does not produce until both inputs are provided
    source ~> zip.in0
                         zip.out ~> Flow[Int].map {s => println(s); s}    ~>  broadcast ~> Sink.ignore
              zip.in1 <~ concat <~ start
                         concat <~ broadcast

  }

//  println("Running cyclic graph")
//  cyclic.run()
//
//  println("Running cyclic fixed")
//  cyclicUnfair.run()
//
//  println("Running cyclic that drops")
//  cyclicFairDropping.run()

  println("Running cyclic with zip")
  cyclicWithZip.run()



}
