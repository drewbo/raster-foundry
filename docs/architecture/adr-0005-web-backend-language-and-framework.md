Web Backend Language and Framework
==================================
Context
-------
A backend web framework is needed in order to handle web requests and coordinate with other components of the backend, such as interacting with the database and starting up batch processing workflows. There are a variety of potential candidates to choose from, including Django (written in Python), Play (written in Scala) and Akka HTTP (the successor of Spray, both of which are written in Scala). The team working on this component of the project has the most experience with Django, which is usually our go-to choice, however there are compelling reasons to investigate using a framework written in Scala for this project.

An important component to consider while making this decision is the tile server, which would likely benefit from being written in Scala, since it could then easily make use of GeoTrellis. The tile server doesn't necessarily need to be implemented within the same framework as the web server, but doing so has advantages. There are many ways to architect the system with separate server components, but each of them requires a form of communication between the two in order to perform actions such as user permission management, and the reading and/or writing of stored objects such as metadata. This may be accomplished by either having one server access the API of the other server, or by connecting to the database directly and potentially duplicating logic. Having a single framework for both of these components can minimize pain points such as these. There is an open [pull request](https://github.com/geotrellis/geotrellis/pull/1459) that aims to allow the ingesting of GeoTrellis Avro records in Python. If this is merged and deemed stable, it could offer a compelling reason to use Django. However, since the pull request has not yet been merged, and given that phase 1 of this project used a Scala tile server, it seems that building upon the initial solution may be the least risky, and fastest way forward.

Regarding the languages themselves: Python is a dynamically typed programming language, and Scala is a statically typed programming language. There are many pros and cons of both. Python is generally easier to more quickly get something up and running. However it has no type-safety, so it is also easier to introduce run-time errors, particularly when the codebase grows to a larger size, and many tests must be written to compensate. The feedback loop between writing code, compiling, and running tests is larger for Scala, but the number of run-time errors is reduced, and it is much easier to perform refactors on the codebase.

The two major Scala backend web frameworks under consideration are Akka HTTP and Play. Akka HTTP is a very lightweight framework (or in their words, not an HTTP framework at all, but rather an Actor-based toolkit for interacting with web services and clients). Its focus is to allow the elegant expression of a web service, and provide tools for testing it. Play is not quite as lightweight, and aims to be more of a Django-type backend web framework. It comes with the ability to serve static assets and perform HTML templating, as well as defining APIs. However, unlike Django, there is no included ORM, so we'd still need to involve a library such as Slick to interact with the database. In this application, the primary use of the backend web framework will be to create an API. We won't be needing any HTML templating functionality, and we'll be using a third party authentication service, so there are fewer reasons to choose Play over Akka HTTP, given that the bulk of the Play functionality will go unused, and we'll need to manually use an ORM for each anyway. Given that we have some previous experience working with Spray (translatable to Akka HTTP), and it's also the GeoTrellis team's primary choice of backend web framework, it seems like a safe choice to go with it.

Decision
--------
For the web backend language on this project we will use Scala, with Akka HTTP as the framework. Despite the fact that as a team we don't have as much Scala experience, the pros outweigh the cons in this scenario. Knowing in advance that we need to interact with a Scala tile server, and that the codebase is likely to be quite large, it will be advantageous to both minimize some intra-server communications pain points discussed above, and to have compile-time error checking and an easier time with refactors. Having a unified language on the backend will also make context-switching less of a burden. Some intra-server communication will still be required, e.g. interaction with Spark cluster jobs, but reducing the total number of components which must communicate with each other will potentially reduce errors and expedite the development process.

Consequences
------------
All team members must be willing to put in effort to extend our experience in Scala. This includes participating in necessary training, instituting best-practices, and interacting with the GeoTrellis team on pair-programming and code reviews.

One potential consequence to be aware of is that Akka HTTP is still relatively young. It is listed as production ready, but there may be potential for bugs to be encountered. The GeoTrellis team has experimented with it, and it seems to have a very similar interface to Spray. In a worst-case scenario, we can potentially switch over to Spray with minimal headache if needed.

Another consequence of using Akka HTTP over Django is that Django is a batteries-included solution which has solutions to many common use-cases built in, whereas Akka HTTP has a much more limited scope. One such item that Django handles well out of the box is connecting to a database and performing migrations. We will need to use another set of libraries to accomplish this within Akka HTTP. The most likely candidate for connecting to the database via an ORM is Slick, which we’ve had good results with in the past and is currently used in GeoTrellis. Slick does not have a built-in way to load a database schema and perform migrations, so we will need to investigate and choose a library for this. Good candidates include: [Scala Forklift](https://github.com/lastland/scala-forklift), [slick-migration-api](https://github.com/nafg/slick-migration-api), and [Flyway](https://flywaydb.org/).