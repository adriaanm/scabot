//package bbj
//import java.util.Date
//import concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.{ Await, Future }
//import scala.concurrent.duration.Duration
//import scala.concurrent.Await.result
//
///** new play.core.StaticApplication(new java.io.File("."))
// *  concurrent.Await.result(bbj.migrate.migrateScalaUntil(500), concurrent.duration.Duration.Inf)
// *
// *  import bbj._
// *  import migrate._
// *  import concurrent.ExecutionContext.Implicits.global
// *  import scala.concurrent.{Await, Future}
// *  import scala.concurrent.duration.Duration
// *  import scala.concurrent.Await.result
// *  import issueTranslation.jiraToYouTrack
// *
// *  def getIssues(from: Int, to: Int) = result(Future.sequence { (from to to).map { jira.getIssue } }.map(_.flatten), Duration.Inf)
// *
// *  val translated = getIssues(1, 500).map(jiraToYouTrack.mapOver).asInstanceOf[Seq[youTrack.issues.Issue]]
// *
// *  translated.flatMap(_.fields("fixedVersion").asInstanceOf[List[youTrack.issues.Version]])
// *  res12 map (v => youTrack.createVersion("SI", v))
// *
// *  result(youTrack.importIssues("SI", translated), Duration.Inf).ahcResponse.getResponseBody
// *
// *
// *  val allIssues = result(jira.allIssues, Duration.Inf)
// *  val allFieldValues = allIssues.flatMap(_.fields).groupBy(_._1).map{case (k, vs) => (k, vs.map(_._2).distinct)}
// *  allFieldValues.keys
// *
// *  allFieldValues("labels").asInstanceOf[Vector[List[String]]].flatten.distinct
// */
//object migrate {
//  val jira = new JiraConnection { val issues = issueTranslation }
//  val youTrack = new YouTrackConnection { val issues = issueTranslation }
//
//  object issueTranslation extends TranslateJiraToYouTrack {
//    val separateFieldJira = Set("status", "project", "labels", "issuelinks", "components", "attachment", "customfield_10005")
//    def xFieldName(n: String): Option[String] = n match {
//      case n if separateFieldJira(n) => None // not used or treated separately
//
//      case "issuekey"                => Some("numberInProject")
//      case "summary"                 => Some(n)
//      case "reporter"                => Some("reporterName")
//      case "created"                 => Some(n)
//      case "updated"                 => Some(n)
//      case "issuetype"               => Some("type")
//      case "priority"                => Some(n)
//
//      case "assignee"                => Some("assigneeName")
//      case "description"             => Some(n)
//      case "environment"             => Some(n) // custom
//      case "resolution"              => Some("state")
//      case "resolutiondate"          => Some("resolved")
//      case "versions"                => Some("affectsVersion")
//      case "fixVersions"             => Some("fixedVersion")
//      case "comment"                 => Some("comment")
//
//      case "votes"                   => Some("voterName")
//      case "watches"                 => Some("watcherName") // also includes customfield_10005
//
//      // customfield_10101, duedate, subtasks, workratio
//      case _                         => println(s"IGNORING FIELD $n"); None
//
//    }
//  }
//
//  import concurrent.ExecutionContext.Implicits.global
//  import issueTranslation.{ Issue, Project, User, Version, jiraToYouTrack }
//
//  def createFieldTypes(projectId: String, allIssues: Seq[Issue]) = {
//    val allFieldValues = allIssues.flatMap(_.fields).groupBy(_._1).map { case (k, vs) => (k, vs.map(_._2).distinct) }.toMap
//    // a new youtrack project already has all these fields -- TODO: determined dynamically
//    val builtin = Set("assigneeName", "created", "summary", "description", "type", "priority", "reporterName", "state", "resolved", "updated", "watcherName", "voterName")
//    val unused = allFieldValues.filter { case (k, v) => v.size <= 1 }.keys.toSet
//    val fieldTypesToCreate = allFieldValues.keys.toSet -- unused -- builtin
//    //Set("environment", "status", "fixVersions", "versions")
//
//    def createVersions(versions: Seq[Version]) =
//      Future.sequence(versions map (youTrack.createVersion(projectId, _)))
//
//    /*def create_value(target, value, field_name, field_type, project_id):
//    if field_type.startswith('user'):
//        create_user(target, value)
//        value['name'] = value['name'].replace(' ', '_')
//    if field_name in jira.EXISTING_FIELDS:
//        return
//    if field_name.lower() not in [field.name.lower() for field in target.getProjectCustomFields(project_id)]:
//        if field_name.lower() not in [field.name.lower() for field in target.getCustomFields()]:
//            target.createCustomFieldDetailed(field_name, field_type, False, True, False, {})
//        if field_type in ['string', 'date', 'integer']:
//            target.createProjectCustomFieldDetailed(project_id, field_name, "No " + field_name)
//        else:
//            bundle_name = field_name + " bundle"
//            create_bundle_safe(target, bundle_name, field_type)
//            target.createProjectCustomFieldDetailed(project_id, field_name, "No " + field_name, {'bundle': bundle_name})
//    if field_type in ['string', 'date', 'integer']:
//        return
//    project_field = target.getProjectCustomField(project_id, field_name)
//    bundle = target.getBundle(field_type, project_field.bundle)
//    try:
//        if 'name' in value:
//            target.addValueToBundle(bundle, value['name'])
//        elif 'value' in value:
//            target.addValueToBundle(bundle, value['value'])
//    except YouTrackException:
//        pass*/
//    def createFieldType(fieldName: String) = fieldName match {
//      // new
//      case "environment" =>
//        youTrack.createCustomField(None, "field", fieldName, youTrack.field.params(
//          typeName = "string", isPrivate = false, defaultVisibility = true, autoAttached = false, emptyFieldText = "Undefined"): _*).map(Seq(_))
//        youTrack.createCustomField(Some(projectId), "field", fieldName, youTrack.field.params(
//          typeName = "string", isPrivate = false, defaultVisibility = true, autoAttached = true, emptyFieldText = "Undefined"): _*).map(Seq(_))
//      case "state" =>
//        youTrack.createCustomField(Some(projectId), "stateBundle", "Not a Bug", ("isResolved", "false"), ("description", "This is not a bug")).map(Seq(_))
//      case "affectsVersion" | "fixedVersion" =>
//        createVersions(allFieldValues(fieldName).asInstanceOf[Seq[Seq[Version]]].flatten)
//      case _ => println("field type: " + fieldName); Future.successful(Nil)
//    }
//
//    Future.sequence(fieldTypesToCreate.map(createFieldType)).map(_.flatten)
//  }
//
//  def migrateIssues(projectId: String, translated: Seq[Issue]) = for {
//    fieldCreation <- createFieldTypes(projectId, translated)
//    req <- youTrack.importIssues(projectId, translated)
//  } yield (fieldCreation, req.ahcResponse.getResponseBody)
//
//  def migrateProject(p: Project, from: Int, to: Int) = for {
//    (translated, allUsers) <- for (issues <- Future.sequence { (from to to).map { jira.getIssue(p.projectId, _) } })
//      yield (issues.flatten.map(jiraToYouTrack.mapOver).asInstanceOf[Seq[Issue]], jira.allUsers)
//    userImport <- youTrack.importUsers(allUsers map (jiraToYouTrack.apply _))
//    project <- youTrack.createProject(p)
//    (fieldCreation, issueImport) <- migrateIssues(p.projectId, translated)
//  } yield (userImport, project, fieldCreation, issueImport)
//
//  def migrateScalaUntil(to: Int) = migrateProject(Project("SI", "Scala", "The Scala Programming Language", "guest"), 1, to) // TODO: set lead to "odersky"
//
//}
//
//trait TranslateJiraToYouTrack extends Issues {
//  def xFieldName(n: String): Option[String]
//
//  // KEY: issuetype has VALUES: Vector(Bug, Improvement, Suggestion, New Feature)
//  def xType(t: String): String = t match {
//    case "Bug"         => "Bug"
//    case "Improvement" => "Feature"
//    case "Suggestion"  => "Feature"
//    case "New Feature" => "Feature"
//  }
//
//  // KEY: priority has VALUES: Vector(Critical, Major, Minor, Blocker, Trivial)
//  def xPriority(t: String): Int = t match {
//    case "Blocker"  => 4
//    case "Critical" => 3
//    case "Major"    => 2
//    case "Minor"    => 1
//    case "Trivial"  => 0
//  }
//
//  // KEY: resolution has VALUES: Vector(Some(Fixed), Some(Not a Bug), None, Some(Won't Fix), Some(Cannot Reproduce), Some(Duplicate), Some(Out of Scope), Some(Incomplete), Some(Fixed, Backport Pending))
//  // KEY: status has VALUES: Vector(Closed, Open)
//  // convert resolution to state (youtrack does not have both status and resolution)
//  def xState(r: Option[String]): String = r match {
//    case None                            => "Open"
//    case Some("Fixed")                   => "Fixed"
//    case Some("Won't Fix")               => "Won't Fix"
//    case Some("Cannot Reproduce")        => "Can't Reproduce"
//    case Some("Duplicate")               => "Duplicate"
//    case Some("Incomplete")              => "Incomplete"
//    case Some("Out of Scope")            => "Won't Fix"
//    case Some("Fixed, Backport Pending") => "To Backport"
//    case Some("Not a Bug")               => "Invalid"
//  }
//
//  // TODO
//  def xComponent(c: String): String = c match {
//    case "Scaladoc Tool"          => "Scaladoc Tool"
//    case "Misc Compiler"          => "Misc Compiler"
//    case "Misc Library"           => "Misc Library"
//    case "Specification"          => "Specification"
//    case "Eclipse Plugin (EOL)"   => "Eclipse Plugin (EOL)"
//    case "Packaging"              => "Packaging"
//    case "Documentation and API"  => "Documentation and API"
//    case "Repl / Interpreter"     => "Repl / Interpreter"
//    case "Pattern Matcher"        => "Pattern Matcher"
//    case "Build, Developer Tools" => "Build, Developer Tools"
//    case "XML Support"            => "XML Support"
//    case "Jira"                   => "Jira"
//    case "Website"                => "Website"
//    case "Actors Library"         => "Actors Library"
//    case "MSIL Backend"           => "MSIL Backend"
//    case "Parser Combinators"     => "Parser Combinators"
//    case "Enumeration"            => "Enumeration"
//    case "Type Checker"           => "Type Checker"
//    case "Swing Library"          => "Swing Library"
//    case "Continuations"          => "Continuations"
//    case "Collections"            => "Collections"
//    case "Specialization"         => "Specialization"
//    case "Type Inference"         => "Type Inference"
//    case "Reflection"             => "Reflection"
//    case "Optimizer"              => "Optimizer"
//    case "Compiler Backend"       => "Compiler Backend"
//    case "Presentation Compiler"  => "Presentation Compiler"
//    case "Macros"                 => "Macros"
//    case "Concurrent Library"     => "Concurrent Library"
//    case "Partest"                => "Partest"
//  }
//
//  def xField(n: String, v: Any) = {
//    xFieldName(n) map (n => (n, (n, v) match {
//      case ("numberInProject", n: String) => n.split("-")(1)
//      case ("type", t: String)            => xType(t)
//      case ("priority", p: String)        => xPriority(p)
//      case ("state", r: Option[String])   => xState(r)
//      case (_, j)                         => jiraToYouTrack.mapOver(j)
//    }))
//  }
//
//  def validUserName(name: String) = !name.contains(" ")
//  def cleanUserName(name: String) = name.replace(" ", "_")
//  val BOGUS_EMAIL = "email.not@specified"
//
//  // TODO: is the user transform done consistently?
//  object jiraToYouTrack extends IssuesTransform {
//    def apply(x: User): User = x match {
//      case User(id, dn, Some(em)) if validUserName(id) => x
//      case User(id, dn, em)                            => User(cleanUserName(id), dn, em orElse Some(BOGUS_EMAIL))(x.groups)
//    }
//    def apply(x: Version): Version = x
//    def apply(x: IssueLinkType): IssueLinkType = x
//
//    override def mapOver(x: Any): Any =
//      x match {
//        //        case Comment(a, b, ua, c, u)         => Comment(apply(a), b, apply(ua), c, u)
//        //        case Attachment(f, a, c, d, s, m, p) => Attachment(f, apply(a), c, d, s, m, p)
//        //        case IssueLink(ilt, o, i)            => IssueLink(apply(ilt), o, i)
//        case Issue(key, fields) => Issue(key, fields flatMap { case (n, v) => xField(n, v) })
//        case x                  => super.mapOver(x)
//      }
//
//  }
//
//  /*
//   allFieldValues("labels").asInstanceOf[Vector[List[String]]].flatten.distinct
//res42: scala.collection.immutable.Vector[String] = Vector(postponed, partialfunction, java-sandbox, depmet, gadt, pattern-matching, type-inference, scaladoc-links, scaladoc, community, spec, string-interpolation, .NET, parametric-extractor, structural-types, xml, existential, error-messages, minimized, parser, tcpoly, soundness, performance, usability, outer-references, switch, java-reflection, should-not-compile, has-pull-request, tailrec, compiler-api, needs_discussion, case-class, grand-challenge, compiler-crash, name-mangling, lub, java-interop, annotations, named-default-args, backport, try-catch, emacs, typetag, varargs, collections, inner-class, scaladoc-types, implicit, binary-compatibility, scalap, runtime-crash, lazy-val, scaladoc-usecases, dependent-types, optimizer, implicit...
//scala> allFieldValues("labels").asInstanceOf[Vector[List[String]]].flatten.distinct.size
//res43: Int = 181
//   *
//   * */
//
//}
//
//
///*
//  def issueToXml(i: Issue): xml.Elem = {
//    val fields = i.fields
//
//    def fieldXml(n: String): xml.NodeSeq = {
//      def opt(f: Any => xml.Elem) = fields(n) match {
//        case Some(x) => f(x)
//        case _       => xml.NodeSeq.Empty
//      }
//      def f(v: Any = fields(n)) = <value>{ v }</value>
//      def df(v: Any = fields(n)) = <value>{ dateYT(v.asInstanceOf[Date]) }</value>
//      def uf(v: Any = fields(n)) = <value>{ v.asInstanceOf[User].name }</value>
//      def lf(v: Any = fields(n))(f: Any => String): xml.NodeSeq = v.asInstanceOf[List[Any]].map { x =>
//        <value>{ f(x) }</value>
//      }
//      def flf(v: Any = fields(n))(f: Any => String): xml.NodeSeq = Await.result(v.asInstanceOf[Future[List[Any]]], Duration.Inf).map { x =>
//        <value>{ f(x) }</value>
//      }
//
//      n match {
//        case "comment" => fields(n).asInstanceOf[List[Comment]] map {
//          case Comment(author, body, _, created, updated) => <comment author={ author.name } text={ body } created={ dateYT(created) } updated={ dateYT(updated) }/>
//        }
//        // dealt with separately
//        case "labels" | "issuelinks" | "attachment" => xml.NodeSeq.Empty
//        case _ =>
//          <field name={ n }>{
//            n match {
//              case "numberInProject" => f() //  long        single    required    Issue number in project. Should be unique
//              case "summary"         => f() //  string      single    required    Issue summary
//              case "reporterName"    => uf() // string      single    required    Login name of issue reporter
//              case "created"         => df() // timestamp   single    required    Issue creation time
//              case "updated"         => df() // timestamp   single    optional    Last issue update time
//              case "type"            => f() //  string      single    required    Issue type name. See Get Issue Types
//              case "priority"        => f() //  integer     single    required    Issue priority ordinal. See Get Issue Priorities
//              case "state"           => f() //  string      single    required    Issue state name. See Get Issue States
//
//              case "assigneeName"    => opt(uf(_)) // string single   optional    Login name of issue assignee. added to assigneeGroup if necessary
//              case "description"     => opt(f(_)) //  string single   optional    Full issue description
//              case "environment"     => opt(f(_))
//              case "resolved"        => opt(df(_)) // timestamp  single optional  Issue resolve time
//              case "due"             => opt(df(_))
//              case "affectsVersion"  => lf() { case Version(n, _, _, _, _) => n } // string    multi   optional    Names of versions affected by the issue. See GET Versions
//              case "fixedVersion"    => lf() { case Version(n, _, _, _, _) => n } // string    multi   optional    Names of versions where the issue is fixed. See GET Versions
//              case "subsystem"       => f() //  string    single    optional    Issue subsystem name. See GET Subsystems
//
//              case "fixedInBuild"    => f() //  string    single    optional    Name of project build where the issue is fixed. Created if necessary.
//              case "updaterName"     => f() //  string    single    optional    Login name of last issue updater
//              case "voterName"       => flf() { case User(n, _, _) => n } // string    multi   optional    Login names of users who voted for the issue
//              case "watcherName"     => flf() { case User(n, _, _) => n }
//              case _ =>
//                println(s"TODO field: $n = ${fields(n)}")
//                xml.NodeSeq.Empty
//            }
//          }</field>
//      }
//    }
//
//    <issue>{ fields.keys.flatMap(fieldXml) }</issue>
//  }
//*/