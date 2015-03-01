package scabot
package jira

import java.io.{DataInputStream, File, FileInputStream, FileOutputStream, InputStream}
import java.text.{ParseException, SimpleDateFormat}
import java.util.Date

import akka.actor.ActorSystem
import spray.client.pipelining._
import spray.http.{Uri, BasicHttpCredentials}
import spray.httpx.unmarshalling.FromStringDeserializer
import spray.json._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.util.Try


trait JiraApi extends JiraApiTypes with JiraJsonProtocol with JiraApiActions {
  self: core.Core with core.Configuration with core.HttpClient =>
}

trait JiraApiTypes {
  self: core.Core with core.Configuration =>

  case class Issue(key: String, fields: Fields)
  case class Fields(summary: String, description: Option[String], comment: Comments, issuelinks: List[IssueLink], attachment: List[Attachment], assignee: Option[User], creator: User, reporter: User, status: Named, resolution: Option[Named],  labels: List[String], components: List[Named], issuetype: Named, priority: Named, environment: Option[String], created:  Date, updated: Date, resolutiondate: Option[Date], versions: List[Version], fixVersions: List[Version],  customfield_10005: Option[List[User]], votes: Votes, watches: Watches)
  case class Attachment(filename: String, author: User, created: Date, content: String, size: Int, mimeType: String) // , properties: Map[String, Any]
  case class Comment(author: User, body: String, updateAuthor: User, created: Date, updated: Date)
  case class Comments(comments: List[Comment], maxResults: Int, total: Int, startAt: Int)
  case class IssueLink(`type`: Named, inwardIssue: Option[Keyed], outwardIssue: Option[Keyed])
  case class User(name: String, displayName: String, emailAddress: Option[String])
  case class Version(name: String, description: Option[String], releaseDate: Option[Date], archived: Boolean, released: Boolean)
  case class Votes(votes: Int)
  case class Watches(watchCount: Int)

  case class Keyed(key: String)
  case class Named(name: String)

//  case class IssueError(errorMessages: List[String])

//  trait IssuesTransform {
//    def apply(x: User): User
//
//    def apply(x: Version): Version
//
//    def apply(x: IssueLinkType): IssueLinkType
//
//    def mapOver(x: Any): Any = {
//      x match {
//        case u: User => apply(u)
//        case v: Version => apply(v)
//        case Comment(a, b, ua, c, u) => Comment(apply(a), b, apply(ua), c, u)
//        case Attachment(f, a, c, d, s, m, p) => Attachment(f, apply(a), c, d, s, m, p)
//        case ilt: IssueLinkType => apply(ilt)
//        case IssueLink(ilt, o, i) => IssueLink(apply(ilt), o, i)
//        case Issue(key, fields) => Issue(key, fields map { case (k, v) => (k, mapOver(v))})
//        case xs: Iterable[Any] => xs.map(mapOver)
//        case x => x
//      }
//    }
//  }

}

// TODO: can we make this more debuggable?
trait JiraJsonProtocol extends JiraApiTypes with DefaultJsonProtocol {
  self: core.Core with core.Configuration =>
  private type RJF[x] = RootJsonFormat[x]

  implicit object dateFmt extends JsonFormat[Date] {
    lazy val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    lazy val shortDateFormat = new SimpleDateFormat("yyyy-MM-dd")

    def read(value: JsValue): Date = value match {
      case JsString(rawDate) =>
        try dateFormat.parse(rawDate)
        catch { case p: ParseException =>
          try shortDateFormat.parse(rawDate)
          catch { case p: ParseException => deserializationError(s"Expected Date format, got $rawDate") }
        }
      case _ => deserializationError(s"Expected JsString, got $value")
    }

    def write(date: Date) = JsString(dateFormat.format(date))
  }

  implicit object _fmtFields extends JsonFormat[Fields] {
    private def w[T: JsonWriter](x: T) = implicitly[JsonWriter[T]].write(x)
    def write(p: Fields): JsObject =
      JsObject(
        ("summary", w(p.summary)),
        ("description", w(p.description)),
        ("comment", w(p.comment)),
        ("issuelinks", w(p.issuelinks)),
        ("attachment", w(p.attachment)),
        ("assignee", w(p.assignee)),
        ("creator", w(p.creator)),
        ("reporter", w(p.reporter)),
        ("status", w(p.status)),
        ("resolution", w(p.resolution)),
        ("labels", w(p.labels)),
        ("components", w(p.components)),
        ("issuetype", w(p.issuetype)),
        ("priority", w(p.priority)),
        ("environment", w(p.environment)),
        ("created", w(p.created)),
        ("updated", w(p.updated)),
        ("resolutiondate", w(p.resolutiondate)),
        ("versions", w(p.versions)),
        ("fixVersions", w(p.fixVersions)),
        ("customfield_10005", w(p.customfield_10005)),
        ("votes", w(p.votes)),
        ("watches", w(p.watches)))

    def read(value: JsValue) = Fields(
      fromField[String](value,             "summary"),
      fromField[Option[String]](value,     "description"),
      fromField[Comments](value,           "comment"),
      fromField[List[IssueLink]](value,    "issuelinks"),
      fromField[List[Attachment]](value,   "attachment"),
      fromField[Option[User]](value,       "assignee"),
      fromField[User](value,               "creator"),
      fromField[User](value,               "reporter"),
      fromField[Named](value,              "status"),
      fromField[Option[Named]](value,      "resolution"),
      fromField[List[String]](value,       "labels"),
      fromField[List[Named]](value,        "components"),
      fromField[Named](value,              "issuetype"),
      fromField[Named](value,              "priority"),
      fromField[Option[String]](value,     "environment"),
      fromField[Date](value,               "created"),
      fromField[Date](value,               "updated"),
      fromField[Option[Date]](value,       "resolutiondate"),
      fromField[List[Version]](value,      "versions"),
      fromField[List[Version]](value,      "fixVersions"),
      fromField[Option[List[User]]](value, "customfield_10005"),
      fromField[Votes](value,              "votes"),
      fromField[Watches](value,            "watches"))
  }

  implicit lazy val _fmtAttachment: RJF[Attachment] = jsonFormat6(Attachment.apply)
  implicit lazy val _fmtComment: RJF[Comment]       = jsonFormat5(Comment.apply)
  implicit lazy val _fmtComments: RJF[Comments]     = jsonFormat4(Comments.apply)
  implicit lazy val _fmtIssue: RJF[Issue]           = jsonFormat2(Issue.apply)
  implicit lazy val _fmtIssueLink: RJF[IssueLink]   = jsonFormat3(IssueLink.apply)
  implicit lazy val _fmtKeyed: RJF[Keyed]           = jsonFormat1(Keyed.apply)
  implicit lazy val _fmtNamed: RJF[Named]           = jsonFormat1(Named.apply)
  implicit lazy val _fmtUser: RJF[User]             = jsonFormat3(User.apply)
  implicit lazy val _fmtVersion: RJF[Version]       = jsonFormat5(Version.apply)
  implicit lazy val _fmtVotes: RJF[Votes]           = jsonFormat1(Votes.apply)
  implicit lazy val _fmtWatches: RJF[Watches]       = jsonFormat1(Watches.apply)
}

trait JiraApiActions extends JiraJsonProtocol {
  self: core.Core with core.Configuration with core.HttpClient =>

  class JiraConnection(config: Config.Jira) {
    def jiraUrl(uri: String) = s"${config.host}$uri"
    def jiraCacheDir = config.cacheDir

    private def fileFor(key: Int) = new File(s"$jiraCacheDir/${key}.json")

    import spray.http.{GenericHttpCredentials, Uri}
    import spray.httpx.SprayJsonSupport._
    import spray.client.pipelining._

    private implicit def connection = setupConnection(config.host, BasicHttpCredentials(config.user, config.pass))

    def api(rest: String) = Uri("/rest/api/latest/" + rest)

    def issue(number: Int) = p[Issue](Get(api("issue" / s"${config.project}-$number")))
  }
}

trait Caching extends JiraJsonProtocol {
  self: core.Core with core.Configuration =>

  private def readFully(in: InputStream, bufferSize: Int = 1024): String = {
    val bytes = new Array[Byte](bufferSize)
    val contents = ArrayBuffer[Byte]()

    @annotation.tailrec
    def read(): Unit =
      in.read(bytes) match {
        case -1 =>
        case count =>
          val data = new Array[Byte](count)
          Array.copy(bytes, 0, data, 0, count)
          contents ++= data
          read()
      }

    read()
    new String(contents.toArray)
  }

  def loadCachedIssue(p: String): Option[Issue] = Try {
    val f = new File(p)
    val data = new Array[Byte](f.length.asInstanceOf[Int])
    Some(jsonReader[Issue].read(JsonParser(readFully(new DataInputStream(new FileInputStream(f))))))
  } getOrElse (None)

}

object jiraCLI extends core.Core with core.Configuration with Caching {
  implicit lazy val system: ActorSystem = ActorSystem("jiraCLI")

  override def tellProjectActor(user: String, repo: String)(msg: jiraCLI.ProjectMessage): Unit = ???

  val issues = 1 to 9200 flatMap {i => println(i); loadCachedIssue(s"/Users/adriaan/Desktop/jira/$i.json")}
}

//
//
//
//private lazy val cacheDir = {
//assert(new File(jiraCacheDir).isDirectory(), s"Please create cache directory $cacheDir to avoid hammering the jira server.")
//cacheDir
//}
//def loadCachedIssue =
//Future(Some {
//val f = fileFor(i)
//val data = new Array[Byte](f.length.asInstanceOf[Int])
//(new DataInputStream(new FileInputStream(f))).readFully(data)
//parseIssue(data)
//})
//
//def cacheIssue(issue: Issue) = {
//val out = new FileOutputStream(fileFor(issue.key))
//try out.write(contents, 0, contents.size)
//finally {
//out.flush();
//out.close()
//}
//}
//
//def getIssue(projectId: String, i: Int): Future[Option[Issue]] = ???




//    implicit object readUser extends Reads[User] {
//      def reads(json: JsValue): JsResult[User] = validate("user")(for (
//        self <- self(json);
//        name <- name(json);
//        emailAddress <- (optField(json)("emailAddress")).map(_.asOpt[String]).orElse(Some(None));  // don't get this field when reading voters/watchers from a lazy list
//        displayName <- (json \ "displayName").asOpt[String]
//      ) yield User(self, name, displayName, emailAddress))
//    }
//
//    /*
//  issues.flatMap(_.fields("versions").asInstanceOf[List[Version]]).toSet
//  Set(Scala 2.10.0-M4, Scala 2.8.1, Scala 2.10.0-M7, Scala 2.9.2, Scala 2.10.0, Scala 2.11.0, Scala 2.10.0-M3, Scala 2.10.0-RC5, Scala 2.9.0,
//      Scala 2.9.1, Scala 2.10.0-M5, Scala 2.9.3-RC1, Scala 2.10.0-RC2, Scala 2.7.7, Scala 2.9.0-1, Scala 2.10.0-M6, Scala 2.10.1-RC1, Scala 2.10.0-M2, Scala 2.10.0-M1, Scala 2.8.0, macro-paradise, Scala 2.11.0-M1, Scala 2.10.0-RC1, Scala 2.10.0-RC3)
//   */
//    implicit object readVersion extends Reads[Version] {
//      def reads(json: JsValue): JsResult[Version] = validate(s"version; got $json")(for (
//        self <- self(json);
//        name <- name(json);
//        description <- (optField(json)("description")).map(_.asOpt[String]).orElse(Some(None));
//        userReleaseDate <- (optField(json)("userReleaseDate")).map(_.asOpt[String]).orElse(Some(None));
//        releaseDate <- (optField(json)("releaseDate")).map(_.asOpt[Date](Reads.DefaultDateReads)).orElse(Some(None));
//        archived <- (json \ "archived").asOpt[Boolean];
//        released <- (json \ "released").asOpt[Boolean]
//      ) yield Version(self, name, description, userReleaseDate, releaseDate, archived, released))
//    }
//
//
//    implicit object readComment extends Reads[Comment] {
//      def reads(json: JsValue): JsResult[Comment] = validate(s"comment; got $json")(for (
//        author <- (json \ "author").asOpt[User];
//        body <- (json \ "body").asOpt[String];
//        updateAuthor <- (json \ "updateAuthor").asOpt[User];
//        created <- (json \ "created").asOpt[Date];
//        updated <- (json \ "updated").asOpt[Date]
//      ) yield Comment(author, body, updateAuthor, created, updated))
//    }
//
//    implicit object readAttachment extends Reads[Attachment] {
//      def reads(json: JsValue): JsResult[Attachment] = validate(s"attachment; got $json")(for (
//        filename <- (json \ "filename").asOpt[String];
//        author <- (json \ "author").asOpt[User];
//        created <- (json \ "created").asOpt[Date];
//        size <- (json \ "size").asOpt[Int];
//        mimeType <- (json \ "mimeType").asOpt[String];
//        properties <- (optField(json)("properties")).map(_.asOpt[JsObject]).orElse(Some(None));
//        content <- (json \ "content").asOpt[String]
//      ) yield Attachment(filename, author, created, content, size, mimeType, properties map (_.value.toMap) getOrElse Map()))
//    }




//  private def cleanEmail(email: String): Option[String] = email match {
//    case "non-valid@e-mail.null" => None
//    case "" => None
//    case e => Some(e)
//  }
//
//
//  private def parseUserReleaseDate(d: String): Option[Date] = {
//    val df = new java.text.SimpleDateFormat("d/MMM/yy")
//    df.setLenient(false)
//    try {
//      Some(df.parse(d))
//    } catch {
//      case _: java.text.ParseException => None
//    }
//  }
//
//  private val versions = collection.mutable.HashMap[String, Version]()
//
//  def Version(self: String, name: String, description: Option[String], userReleaseDate: Option[String], releaseDate: Option[Date], archived: Boolean, released: Boolean) =
//    versions.synchronized {
//      versions.getOrElseUpdate(self, new Version(name, description, releaseDate orElse userReleaseDate.flatMap(parseUserReleaseDate), archived, released))
//    }
//
//  def allVersions = versions.values
//
//  /** scala> issues.flatMap(_.fields("issuelinks").asInstanceOf[List[IssueLink]].map(_.name)).distinct
//    * Vector(Relates, Duplicate, Blocks, Cloners)
//    *
//    * scala> issues.flatMap(_.fields("issuelinks").asInstanceOf[List[IssueLink]].map(_.inward)).distinct
//    * Vector(relates to, is duplicated by, is blocked by, is cloned by)
//    *
//    * scala> issues.flatMap(_.fields("issuelinks").asInstanceOf[List[IssueLink]].map(_.outward)).distinct
//    * Vector(relates to, duplicates, blocks, clones)
//    *
//    */
//  def readIssueLink(selfKey: String): Reads[IssueLink] = new Reads[IssueLink] {
//    def reads(json: JsValue): JsResult[IssueLink] = validate(s"issue link; got $json")(for (
//      name <- name(json \ "type");
//      inward <- (json \ "type" \ "inward").asOpt[String];
//      outward <- (json \ "type" \ "outward").asOpt[String];
//      outwardIssue <- (for (
//        out <- (optField(json)("outwardIssue"));
//        out <- out.asOpt[JsObject]
//      ) yield (out \ "key").asOpt[String]).orElse(Some(None));
//      inwardIssue <- (for (
//        in <- (optField(json)("inwardIssue"));
//        in <- in.asOpt[JsObject]
//      ) yield (in \ "key").asOpt[String]).orElse(Some(None))
//    ) yield {
//      val ilt = IssueLinkType(name, inward, outward)
//      (inwardIssue, outwardIssue) match {
//        case (Some(i), None) => IssueLink(ilt, i, selfKey) // read as: s"$i ${ilt.name} $selfKey"
//        case (None, Some(o)) => IssueLink(ilt, selfKey, o) // read as: s"$selfKey ${ilt.name} $o"
//      }
//    })
//  }
//
//  def IssueLinkType(name: String, inward: String, outward: String) =
//    (name, inward, outward) match {
//      case ("Relates", "relates to", "relates to") => Relates
//      case ("Duplicate", "is duplicated by", "duplicates") => Duplicates
//      case ("Blocks", "is blocked by", "blocks") => Blocks
//      case ("Cloners", "is cloned by", "clones") => Clones
//    }
//
//    //  lazy val allIssues: Future[IndexedSeq[Issue]] =
//    //    Future.sequence { (1 to lastIssue).map { getIssue(projectId, _) } }.map(_.flatten)
//
//    def parseIssue(data: Array[Byte]): Issue = parseIssue(Json.parse(new String(data)))
//    def parseIssue(i: JsValue): Issue =
//      optField(i)("errorMessages").map(_.as[List[String]]) match {
//        case Some(errorMessages) =>
//          if (errorMessages contains "Issue Does Not Exist") throw new NoSuchElementException(errorMessages.mkString("\n"))
//          else throw new IllegalArgumentException(errorMessages.mkString("\n"))
//        case None =>
//          val selfKey = (i \ "key").as[String]
//          Issue(
//            selfKey,
//            (i \ "fields").as[Map[String, JsValue]].map { case (k, v) => parseField(selfKey, k, v) } + ("issuekey" -> selfKey)) // , (i \ "changelog" \ "histories").as[List[JsValue]])
//      }
//
//    def parseField(selfKey: String, field: String, v: JsValue): (String, Any) = (field,
//      try field match {
//        case "project"           => (v \ "key").as[String] // Project
//        case "issuekey"          => v.as[String] // not normally parsed -- overridden in parseIssue
//        case "summary"           => v.as[String]
//        case "reporter"          => v.as[User]
//        case "created"           => v.as[Date]
//        case "updated"           => v.as[Date]
//        case "issuetype"         => (v \ "name").as[String] // IssueType: (Bug, Improvement, Suggestion, New Feature)
//        case "priority"          => (v \ "name").as[String] // Priority: (Critical, Major, Minor, Blocker, Trivial)
//        case "status"            => (v \ "name").as[String] // Status: (Open, Closed)
//
//        case "assignee"          => v.asOpt[User]
//        case "description"       => v.asOpt[String]
//        case "environment"       => v.asOpt[String] // TODO: extract labels -- this field is extremely messy
//        case "resolution"        => optField(v)("name").map(_.as[String]) // "Fixed", "Not a Bug", "Won't Fix", "Cannot Reproduce", "Duplicate", "Out of Scope", "Incomplete", "Fixed, Backport Pending"
//        case "resolutiondate"    => v.asOpt[Date]
//        case "duedate"           => v.asOpt[Date]
//        case "versions"          => v.as[List[Version]] // affected version
//        case "fixVersions"       => v.as[List[Version]]
//        case "labels"            => v.as[List[String]]
//        case "issuelinks"        =>
//          implicit val readIL = readIssueLink(selfKey); v.as[List[IssueLink]]
//        case "components"        => v.as[List[JsObject]].map(c => (c \ "name").as[String])
//        case "comment"           => (v \ "comments").as[List[Comment]] // List[Comment]
//        case "attachment"        => v.as[List[Attachment]]
//
//        case "votes"             => lazyList[User](v, "votes", "voters") // Future[List[User]]
//        case "watches"           => lazyList[User](v, "watchCount", "watchers") // Future[List[User]]
//
//        case "customfield_10005" => v.asOpt[List[User]] // trac cc
//        case "customfield_10101" => v.asOpt[List[String]] // flagged -- never set
//        //      case "customfield_10104" => v.asOpt[Float] // Story points
//        //      case "customfield_10105" => v.asOpt[Float] // business value
//        case "subtasks" =>
//          assert(v.as[List[JsValue]].isEmpty, "subtasks not supported"); v
//        case "workratio" => v.asOpt[Float] // always -1
//      } catch {
//        case e: Exception => throw new Exception(s"Error parsing field $field : $v", e)
//      })
//
