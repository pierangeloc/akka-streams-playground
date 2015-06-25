import java.nio.ByteOrder

import akka.stream.BidiShape
import akka.stream.scaladsl.{Flow, BidiFlow}
import akka.stream.stage._
import akka.util.ByteString


//serialization and deserialization custom protocol
object Protocols {

  trait Message
  case class Ping(id: Int) extends Message
  case class Pong(id: Int) extends Message

  //2 different ways of serializing ping and pong messages
  def toBytes(msg: Message): ByteString = {
    msg match {
      case Ping(id) => ByteString.newBuilder.putByte(1).putInt(id).result()
      case Pong(id) => ByteString.newBuilder.putByte(2).putInt(id).result()
    }
  }

  def fromBytes(bytes: ByteString): Message = {
    val it = bytes.iterator
    it.getByte match {
      case 1 => Ping(it.getInt)
      case 2 => Pong(it.getInt)
      case other => throw new RuntimeException(s"Parse error, got $other while expecting 1|2")
    }
  }


  /**
   * Define a custom bidiflow that adds the length header and removes it on the way back
   */

  implicit val order = ByteOrder.LITTLE_ENDIAN

  val framing = BidiFlow() { builder =>

    def addLengthHeader(bytes: ByteString): ByteString = {
      ByteString.newBuilder.putInt(bytes.length).append(bytes).result()
    }

    /**
     * Define custom transformation for header management
     */
    class FrameParser extends PushPullStage[ByteString, ByteString] {

      //stash bytes not yet parsed
      var stash = ByteString.empty
      //current message length
      var needed = -1

      override def onPush(bytes: ByteString, ctx: Context[ByteString]) = {
        stash ++ bytes
        run(ctx)
      }

      override def onPull(ctx: Context[ByteString]) = run(ctx)

      override def onUpstreamFinish(ctx: Context[ByteString]): TerminationDirective = {
        if(stash.isEmpty) ctx.finish()
          //ignore the termination and emit what you have to emit still
        else ctx.absorbTermination()
      }


      def run(ctx: Context[ByteString]): SyncDirective = {
        if(needed == -1) {
          if (stash.length < 4) pullOrFinish(ctx)
          else {
            needed = stash.iterator.getInt
            stash.drop(4) //drop the int
            run(ctx)
          }
        } else if (stash.length < needed) {
          //we didn't accumulate yet all the needed bytes, we need to accumulate more
          pullOrFinish(ctx)
        } else {
          //we have all the required bytes, put them on downstream
          val emit = stash.take(needed)
          stash = stash.drop(needed)
          needed = -1
          ctx.push(emit)
        }
      }

      def pullOrFinish(ctx: Context[ByteString]): SyncDirective = {
        if(ctx.isFinishing) ctx.finish()
        else ctx.pull()
      }

    }

    //top flow of the bidi
    val outbound = builder.add(Flow[ByteString].map(addLengthHeader))
    //bottom flow
    val inbound = builder.add(Flow[ByteString].transform(() => new FrameParser))


    BidiShape(outbound, inbound)
  }

}
