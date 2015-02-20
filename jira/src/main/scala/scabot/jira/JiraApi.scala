package scabot
package jenkins

import java.io.{DataInputStream, File, FileInputStream, FileOutputStream, InputStream}
import java.util.Date

import spray.client.pipelining._
import spray.http.{Uri, BasicHttpCredentials}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future


trait JiraApi extends JiraApiTypes with JiraJsonProtocol with JiraApiActions {
  self: core.Core with core.Configuration with core.HttpClient =>
}
/*
{
    "expand": "renderedFields,names,schema,transitions,operations,editmeta,changelog",
    "fields": {
        "assignee": null,
        "attachment": [
            {
                "author": {
                    "active": true,
                    "displayName": "Olivier Chafik",
                    "name": "ochafik",
                    "self": "https://issues.scala-lang.org/rest/api/2/user?username=ochafik"
                },
                "content": "https://issues.scala-lang.org/secure/attachment/16511/bug.scala",
                "created": "2015-02-16T00:22:33.000+0100",
                "filename": "bug.scala",
                "id": "16511",
                "mimeType": "application/octet-stream",
                "self": "https://issues.scala-lang.org/rest/api/2/attachment/16511",
                "size": 358
            },
        ],
        "comment": {
            "comments": [                {
                    "author": {
                        "active": true,
                        "displayName": "Ismael Juma",
                        "name": "ijuma",
                        "self": "https://issues.scala-lang.org/rest/api/2/user?username=ijuma"
                    },
                    "body": "Adding link to my message that explains what I think is missing in the bytecode with a test that fails in 2.7.x too:\n\nhttp://article.gmane.org/gmane.comp.lang.scala.internals/1337",
                    "created": "2009-09-27T06:40:55.000+0200",
                    "id": "46401",
                    "self": "https://issues.scala-lang.org/rest/api/2/issue/21609/comment/46401",
                    "updateAuthor": {
                        "active": true,
                        "displayName": "Ismael Juma",
                        "name": "ijuma",
                        "self": "https://issues.scala-lang.org/rest/api/2/user?username=ijuma"
                    },
                    "updated": "2009-09-27T06:40:55.000+0200"
                },],
            "maxResults": 0,
            "startAt": 0,
            "total": 0
        },
        "components": [
            {
                "id": "10601",
                "name": "Type Inference",
                "self": "https://issues.scala-lang.org/rest/api/2/component/10601"
            }
        ],
        "created": "2015-02-16T00:22:33.000+0100",
        "creator": {
            "active": true,
            "displayName": "Olivier Chafik",
            "name": "ochafik",
            "self": "https://issues.scala-lang.org/rest/api/2/user?username=ochafik"
        },
        "customfield_10005": [
            {
                "active": true,
                "displayName": "Ismael Juma",
                "name": "ijuma",
                "self": "https://issues.scala-lang.org/rest/api/2/user?username=ijuma"
            },
            {
                "active": true,
                "displayName": "Paul Phillips",
                "name": "extempore",
                "self": "https://issues.scala-lang.org/rest/api/2/user?username=extempore"
            }
        ],
        "customfield_10101": null,
        "description": "The following repo case crashes scalac 2.11 and 2.10 with an OOM exception, or hangs pretty much forever if scalac is given loads of memory.\r\n\r\nI haven't managed to reduce it further, but I'm under the impression it has to do with the widening of type constraints in the nested maps (where empty strings are returned).\r\n\r\n{code:title=bug.scala|borderStyle=solid}\r\n// scalac bug.scala\r\nclass ImGonnaTakeForeverToCompile {\r\n  abstract class Foo extends Ordered[Foo]\r\n\r\n  val seq: Seq[Int] = null\r\n  val set: Set[Int] = null\r\n  val map: Map[Int, Int] = null\r\n  val values: Map[Int, Set[Foo]] = null\r\n\r\n  map ++ set.map(_ -> \"\")\r\n\r\n  values ++ seq.groupBy(_ / 2).toSeq.map({\r\n    case (key, group) =>\r\n      key -> (values(key) ++ group.map(_ => \"\"))\r\n  })\r\n}\r\n{code}\r\n\r\n(I fixed my original non-reduced code by avoiding a widening that was introduced by mistake)\r\n\r\nA quick jconsole sampling shows some / lots of time is spent in `scala.reflect.internal.tpe.TypeConstraints.solve`.",
        "duedate": null,
        "environment": "MacOS X + Java 1.8",
        "fixVersions": [],
        "issuelinks": [
        {"id":"12114","self":"https://issues.scala-lang.org/rest/api/2/issueLink/12114",
        "type":{"id":"10003","name":"Relates","inward":"relates to","outward":"relates to","self":"https://issues.scala-lang.org/rest/api/2/issueLinkType/10003"},"inwardIssue":{"id":"28176","key":"SI-7637","self":"https://issues.scala-lang.org/rest/api/2/issue/28176","fields":{"summary":"REPL :edit command","status":{"self":"https://issues.scala-lang.org/rest/api/2/status/6","description":"The issue is considered finished, the resolution is correct. Issues which are closed can be reopened.","iconUrl":"https://issues.scala-lang.org/images/icons/statuses/closed.png","name":"CLOSED","id":"6","statusCategory":{"self":"https://issues.scala-lang.org/rest/api/2/statuscategory/3","id":3,"key":"done","colorName":"green","name":"Complete"}},"priority":{"self":"https://issues.scala-lang.org/rest/api/2/priority/3","iconUrl":"https://issues.scala-lang.org/images/icons/priorities/major.png","name":"Major","id":"3"},"issuetype":{"self":"https://issues.scala-lang.org/rest/api/2/issuetype/4","id":"4","description":"An improvement or enhancement to an existing feature or task.","iconUrl":"https://issues.scala-lang.org/images/icons/issuetypes/improvement.png","name":"Improvement","subtask":false}}}}
        ],
        "issuetype": {
            "description": "A problem which impairs or prevents the functions of the product.",
            "iconUrl": "https://issues.scala-lang.org/images/icons/issuetypes/bug.png",
            "id": "1",
            "name": "Bug",
            "self": "https://issues.scala-lang.org/rest/api/2/issuetype/1",
            "subtask": false
        },
        "labels": [
            "compiler-crash",
            "does-not-compile"
        ],
        "lastViewed": "2015-02-19T22:35:08.300+0100",
        "priority": {
            "iconUrl": "https://issues.scala-lang.org/images/icons/priorities/major.png",
            "id": "3",
            "name": "Major",
            "self": "https://issues.scala-lang.org/rest/api/2/priority/3"
        },
        "project": {
            "id": "10005",
            "key": "SI",
            "name": "Scala Programming Language",
            "self": "https://issues.scala-lang.org/rest/api/2/project/10005"
        },
        "reporter": {
            "active": true,
            "displayName": "Olivier Chafik",
            "name": "ochafik",
            "self": "https://issues.scala-lang.org/rest/api/2/user?username=ochafik"
        },
        "resolution": {
            "description": "A fix for this issue is checked into the tree and tested.",
            "id": "1",
            "name": "Fixed",
            "self": "https://issues.scala-lang.org/rest/api/2/resolution/1"
        },
        "resolutiondate": null,
        "status": {
            "description": "The issue is open and ready for the assignee to start work on it.",
            "iconUrl": "https://issues.scala-lang.org/images/icons/statuses/open.png",
            "id": "1",
            "name": "Open",
            "self": "https://issues.scala-lang.org/rest/api/2/status/1",
            "statusCategory": {
                "colorName": "blue-gray",
                "id": 2,
                "key": "new",
                "name": "New",
                "self": "https://issues.scala-lang.org/rest/api/2/statuscategory/2"
            }
        },
        "subtasks": [],
        "summary": "OutOfMemory / hang of scalac (typer bug?)",
        "updated": "2015-02-16T00:22:33.000+0100",
        "versions": [
            {
                "archived": false,
                "id": "11900",
                "name": "Scala 2.10.4",
                "releaseDate": "2014-03-18",
                "released": true,
                "self": "https://issues.scala-lang.org/rest/api/2/version/11900"
            },
            {
                "archived": false,
                "id": "12102",
                "name": "Scala 2.11.4",
                "releaseDate": "2014-10-30",
                "released": true,
                "self": "https://issues.scala-lang.org/rest/api/2/version/12102"
            }
        ],
        "votes": {
            "hasVoted": false,
            "self": "https://issues.scala-lang.org/rest/api/2/issue/SI-9151/votes",
            "votes": 0
        },
        "watches": {
            "isWatching": false,
            "self": "https://issues.scala-lang.org/rest/api/2/issue/SI-9151/watchers",
            "watchCount": 2
        },
        "workratio": -1
    },
    "id": "30292",
    "key": "SI-9151",
    "self": "https://issues.scala-lang.org/rest/api/latest/issue/30292"
}

*/
trait JiraApiTypes {
  self: core.Core with core.Configuration =>

  case class Project(projectId: String, name: String, description: String, lead: String, startingNumber: Int = 1)

  case class User(name: String, displayName: String, emailAddress: Option[String])(val groups: concurrent.Future[List[String]]) {
    override def toString = displayName
  }

  case class Version(name: String, description: Option[String], releaseDate: Option[Date], archived: Boolean, released: Boolean) {
    override def toString = name
  }

  case class Comment(author: User, body: String, updateAuthor: User, created: Date, updated: Date) {
    override def toString = s"on $created, $author said '$body' ($updated, $updateAuthor)"
  }

  case class Attachment(filename: String, author: User, created: Date, content: String, size: Int, mimeType: String, properties: Map[String, Any])

  sealed class IssueLinkType(val name: String, val inward: String, val outward: String) {
    def directed = outward != inward
  }

  case object Relates extends IssueLinkType("Relates", "relates to", "relates to")

  case object Duplicates extends IssueLinkType("Duplicates", "is duplicated by", "duplicates")

  case object Blocks extends IssueLinkType("Blocks", "is blocked by", "blocks")

  case object Clones extends IssueLinkType("Clones", "is cloned by", "clones")

  case class IssueLink(`type`: Named, sourceKey: String, targetKey: String) {
    override def toString = s"$sourceKey ${kind.name} $targetKey"
  }

  case class Named(name: String)
  case class Comments(comments: List[Comment], maxResults: Int, total: Int, startAt: Int)
  case class Votes(votes: Int)
  case class Watches(watchCount: Int)

  case class Fields(
    summary: String,
    description: String,
    comment: Comments,
    issuelinks: List[IssueLink],
    attachment: List[Attachment],
    assignee: User,
    creator: User,
    reporter: User,
    status: Named,
    resolution: Named,
    labels: List[String],
    components: List[Named],
    issuetype: Named,
    priority: Named,
    environment: String,
    created: Date,
    updated: Date,
    resolutiondate: Date,
    versions: List[Version],
    fixVersions: List[Version],
    customfield_10005: List[User],
    votes: Votes,
    watches: Watches)

  case class Issue(key: String, fields: Fields)

  trait IssuesTransform {
    def apply(x: User): User

    def apply(x: Version): Version

    def apply(x: IssueLinkType): IssueLinkType

    def mapOver(x: Any): Any = {
      x match {
        case u: User => apply(u)
        case v: Version => apply(v)
        case Comment(a, b, ua, c, u) => Comment(apply(a), b, apply(ua), c, u)
        case Attachment(f, a, c, d, s, m, p) => Attachment(f, apply(a), c, d, s, m, p)
        case ilt: IssueLinkType => apply(ilt)
        case IssueLink(ilt, o, i) => IssueLink(apply(ilt), o, i)
        case Issue(key, fields) => Issue(key, fields map { case (k, v) => (k, mapOver(v))})
        case xs: Iterable[Any] => xs.map(mapOver)
        case x => x
      }
    }
  }

}

}

// TODO: can we make this more debuggable?
trait JiraJsonProtocol extends JiraApiTypes with DefaultJsonProtocol {
  self: core.Core with core.Configuration =>
  private type RJF[x] = RootJsonFormat[x]
//  implicit lazy val _fmtJob: RJF[Job] = jsonFormat7(Job.apply)

}

trait JiraApiActions extends JiraJsonProtocol {
  self: core.Core with core.Configuration with core.HttpClient =>

  class JiraConnection(config: Config.Jira) {
    def jiraUrl(uri: String) = s"${config.host}$uri"
    def jiraCacheDir = config.cacheDir


    private def fileFor(key: Int) = new File(s"$cacheDir/${key}.json")

    import spray.http.{GenericHttpCredentials, Uri}
    import spray.httpx.SprayJsonSupport._
    import spray.client.pipelining._

    private implicit def connection = setupConnection(config.host, BasicHttpCredentials(config.user, config.pass))

    def api(rest: String) = Uri("/rest/api/latest/" + rest)


    def issue(number: Int) = p[Issue](Get(api("issue" / s"${config.project}-$number")))

  }
}

//
//
//private def readFully(in: InputStream, bufferSize: Int = 1024): Array[Byte] = {
//val bytes = new Array[Byte](bufferSize)
//val contents = ArrayBuffer[Byte]()
//
//@annotation.tailrec
//def read(): Unit =
//in.read(bytes) match {
//case -1 =>
//case count =>
//val data = new Array[Byte](count)
//Array.copy(bytes, 0, data, 0, count)
//contents ++= data
//read()
//}
//
//read()
//contents.toArray
//}
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
