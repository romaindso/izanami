package domains.abtesting

import java.time.LocalDateTime

import akka.stream.scaladsl.Source
import akka.{Done, NotUsed}
import domains.Key
import domains.abtesting.Experiment.ExperimentKey
import libs.IdGenerator
import play.api.libs.json._
import store.Result.Result
import store.{FindResult, StoreOps}

import scala.concurrent.Future

/* ************************************************************************* */
/*                      ExperimentVariantEvent                               */
/* ************************************************************************* */

case class ExperimentVariantEventKey(experimentId: ExperimentKey,
                                     variantId: String,
                                     clientId: String,
                                     namespace: String,
                                     id: String) {
  def key: Key =
    Key.Empty / experimentId / variantId / clientId / namespace / id
}

object ExperimentVariantEventKey {

  private val idGenerator = IdGenerator(1024)

  implicit val format: Format[ExperimentVariantEventKey] = Format(
    Key.format.map { k =>
      ExperimentVariantEventKey(k)
    },
    Writes[ExperimentVariantEventKey](vk => Key.format.writes(vk.key))
  )

  def apply(key: Key): ExperimentVariantEventKey = {
    val id :: pattern :: clientId :: variantId :: experimentId =
      key.segments.toList.reverse
    ExperimentVariantEventKey(Key(experimentId.reverse), variantId, clientId, pattern, id)
  }

  def generateId: String = s"${idGenerator.nextId()}"
}

sealed trait ExperimentVariantEvent {
  def id: ExperimentVariantEventKey
  def variant: Variant
  def date: LocalDateTime
}

case class ExperimentVariantDisplayed(id: ExperimentVariantEventKey,
                                      experimentId: ExperimentKey,
                                      clientId: String,
                                      variant: Variant,
                                      date: LocalDateTime = LocalDateTime.now(),
                                      transformation: Double,
                                      variantId: String)
    extends ExperimentVariantEvent

object ExperimentVariantDisplayed {
  implicit val format = Json.format[ExperimentVariantDisplayed]
}

case class ExperimentVariantWon(id: ExperimentVariantEventKey,
                                experimentId: ExperimentKey,
                                clientId: String,
                                variant: Variant,
                                date: LocalDateTime = LocalDateTime.now(),
                                transformation: Double,
                                variantId: String)
    extends ExperimentVariantEvent

object ExperimentVariantWon {
  implicit val format = Json.format[ExperimentVariantWon]
}

object ExperimentVariantEvent {

  private val reads: Reads[ExperimentVariantEvent] =
    Reads[ExperimentVariantEvent] {
      case event
          if (event \ "@type")
            .asOpt[String]
            .contains("VariantDisplayedEvent") =>
        ExperimentVariantDisplayed.format.reads(event)
      case event if (event \ "@type").asOpt[String].contains("VariantWonEvent") =>
        ExperimentVariantWon.format.reads(event)
      case other => JsError("error.bad.format")
    }

  private val writes: Writes[ExperimentVariantEvent] =
    Writes[ExperimentVariantEvent] {
      case e: ExperimentVariantDisplayed =>
        ExperimentVariantDisplayed.format.writes(e) ++ Json.obj("@type" -> "VariantDisplayedEvent")
      case e: ExperimentVariantWon =>
        ExperimentVariantWon.format.writes(e).as[JsObject] ++ Json.obj("@type" -> "VariantWonEvent")
    }

  implicit val format = Format(reads, writes)
}

trait ExperimentVariantEventStore extends StoreOps {

  def create(id: ExperimentVariantEventKey, data: ExperimentVariantEvent): Future[Result[ExperimentVariantEvent]]

  def deleteEventsForExperiment(experiment: Experiment): Future[Result[Done]]

  def findVariantResult(experiment: Experiment): FindResult[VariantResult]

  def listAll(patterns: Seq[String] = Seq("*")): Source[ExperimentVariantEvent, NotUsed]

}
