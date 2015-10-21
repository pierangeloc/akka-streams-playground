package stream

import akka.stream.scaladsl.Tcp.{IncomingConnection, OutgoingConnection, ServerBinding}
import akka.stream.scaladsl.{Concat, Flow, Source, Tcp}
import akka.stream.stage.{Context, PushStage, StatefulStage, SyncDirective}
import akka.util.ByteString

import scala.annotation.tailrec
import scala.concurrent.Future

object REPL  extends App with StreamingFacilities {

  //setup a server
  val connectionsSource: Source[IncomingConnection, Future[ServerBinding]] = Tcp().bind("localhost", 8085)
  connectionsSource.runForeach { connection =>
    val serverLogic = Flow() { implicit builder =>
      import akka.stream.scaladsl.FlowGraph.Implicits._

      val commandParser = new PushStage[String, String] {
        override def onPush(elem: String, ctx: Context[String]): SyncDirective = {
          elem match {
            case "BYE" => ctx.finish() //stop the flow
            case _ => ctx.push(elem + "!");
          }
        }
      }

      val welcomeMessage = s"Welcome to: ${connection.localAddress}, you are ${connection.remoteAddress}\n"
      val welcome = Source.single(ByteString(welcomeMessage))

      val echo = builder.add(Flow[ByteString] //for every incoming connection
        .transform(() => RecipeParseLines.parseLines("\n", 256)) //transform bytes to strings
        .transform(() => commandParser) //parse the strings and generate the responses
        .map(_ + "\n") //make new lines from them
        .map(ByteString(_)) //make bytes and send to client
      )

      val concat = builder.add(Concat[ByteString]())

      //concatenate 1 given source message to the ones coming from the connection, so the conversation can start!
      welcome ~> concat.in(0)
      echo.outlet ~> concat.in(1)

      (echo.inlet, concat.out)
    }

    connection.handleWith(serverLogic)
  }

  //setup a client
  val connection: Flow[ByteString, ByteString, Future[OutgoingConnection]] = Tcp().outgoingConnection("localhost", 8085)

  //stage that pushes on demand
  val replParser = new PushStage[String, ByteString] {
    override def onPush(elem: String, ctx: Context[ByteString]): SyncDirective = {
      elem match {
        case "q" => ctx.pushAndFinish(ByteString("BYE\n"))
        case _ => ctx.push(ByteString(s"$elem\n"))
      }
    }
  }

  //we receive bytes from the server, we transform them producing the next request
  val repl = Flow[ByteString]
    .transform(() => RecipeParseLines.parseLines("\n", 256)) //emit 1 string for every end of line
    .map(text => println("Server: " + text)) //log what we got from the server
    .map(_ => readLine("> ")) //type something
    .transform(() => replParser) //transform it into bytes

  connection.join(repl).run() //get bytes from server, prints as string, input new bytes, send them over to server



  object RecipeParseLines {

    //factory for 1 stage from bytes to string that reads from bytes, detects line termination, and builds up strings to be emitted
    def parseLines(separator: String, maximumLineBytes: Int) =
      new StatefulStage[ByteString, String] {
        private val separatorBytes = ByteString(separator)
        private val firstSeparatorByte = separatorBytes.head
        private var buffer = ByteString.empty
        private var nextPossibleMatch = 0
        def initial = new State {
          override def onPush(chunk: ByteString, ctx: Context[String]): SyncDirective = {
            buffer ++= chunk
            if (buffer.size > maximumLineBytes)
              ctx.fail(new IllegalStateException(s"Read ${buffer.size} bytes " +
                s"which is more than $maximumLineBytes without seeing a line terminator"))
            else emit(doParse(Vector.empty).iterator, ctx)
          }
          @tailrec
          private def doParse(parsedLinesSoFar: Vector[String]): Vector[String] = {
            val possibleMatchPos = buffer.indexOf(firstSeparatorByte, from = nextPossibleMatch)
            if (possibleMatchPos == -1) {
              // No matching character, we need to accumulate more bytes into the buffer
              nextPossibleMatch = buffer.size
              parsedLinesSoFar
            } else if (possibleMatchPos + separatorBytes.size > buffer.size) {
              // We have found a possible match (we found the first character of the terminator
              // sequence) but we don’t have yet enough bytes. We remember the position to
              // retry from next time.
              nextPossibleMatch = possibleMatchPos
              parsedLinesSoFar
            } else {
              if (buffer.slice(possibleMatchPos, possibleMatchPos + separatorBytes.size)
                == separatorBytes) {
                // Found a match
                val parsedLine = buffer.slice(0, possibleMatchPos).utf8String
                buffer = buffer.drop(possibleMatchPos + separatorBytes.size)
                nextPossibleMatch -= possibleMatchPos + separatorBytes.size
                doParse(parsedLinesSoFar :+ parsedLine)
              } else {
                nextPossibleMatch += 1
                doParse(parsedLinesSoFar)
              }
            }
          }
        }
      }
  }


}
