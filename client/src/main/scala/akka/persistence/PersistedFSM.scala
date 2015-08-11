/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package akka.persistence

import java.io.{PrintWriter, StringWriter}

import akka.actor.FSM._
import akka.actor.{ActorRef, FSM}

trait PersistedFSM[S, D] extends PersistentActor with FSM[S, D]{
  private var replyAfterSaveMsg: Option[Any] = None
  private var listenersForSnapshotSave: Map[Long, RecipientWithMsg] = Map.empty

  implicit class StateExt(state: State) {
    def replyingAfterSave(msg: Any = StateSaved): PersistedFSM.this.State = {
      replyAfterSaveMsg = Some(msg)
      state
    }
  }


  override def receiveRecover: Receive = {
    case SnapshotOffer(metadata, stateAndData) =>
      log.info(s"Recovering: $persistenceId from snapshot: $stateAndData")
      val casted = stateAndData.asInstanceOf[StateAndData[S, D]]
      startWith(casted.state, casted.data)
  }

  override def receiveCommand: Receive = {
    case _ => throw new IllegalArgumentException("Should be used receive of FSM")
  }

  onTransition {
    case (_, to) =>
      deleteSnapshotsLogging()
      saveSnapshotLogging(StateAndData(to, nextStateData))
  }

  onTermination {
    case StopEvent(Normal, _, _) =>
      deleteSnapshotsLogging()
    case StopEvent(Failure(_), _, _) =>
      deleteSnapshotsLogging()
  }

  private def deleteSnapshotsLogging() = {
    log.debug(s"Deleting all snapshots for $persistenceId ...")
    deleteSnapshots(SnapshotSelectionCriteria())
  }

  private def saveSnapshotLogging(stateAndData: StateAndData[S, D]) = {
    log.debug(s"Saving state and data for $persistenceId: $stateAndData ...")
    updateLastSequenceNr(lastSequenceNr + 1)
    replyAfterSaveMsg.foreach { msg =>
      listenersForSnapshotSave += lastSequenceNr -> new RecipientWithMsg(sender(), msg)
      replyAfterSaveMsg = None
    }
    saveSnapshot(stateAndData)
  }

  override def receive: Receive = logSnapshotEvents orElse super.receive

  protected val logSnapshotEvents: Receive = {
    case SaveSnapshotSuccess(metadata) =>
      log.debug("State saved for " + persistenceId)
      replyToSaveListenerIfWaiting(metadata)
      stay()
    case SaveSnapshotFailure(metadata, cause) =>
      val stringWriter = new StringWriter()
      val printWriter = new PrintWriter(stringWriter)
      cause.printStackTrace(printWriter)
      log.error(s"State save failure for $persistenceId.\nError: $stringWriter")
      stay()
  }

  private def replyToSaveListenerIfWaiting(metadata: SnapshotMetadata): Unit = {
    listenersForSnapshotSave.get(metadata.sequenceNr).foreach { listener =>
      listener.reply()
      listenersForSnapshotSave -= metadata.sequenceNr
    }
  }
  
  class RecipientWithMsg(recipient: ActorRef, msg: Any) {
    def reply() = recipient ! msg
  }
}

case class StateAndData[S, D](state: S, data: D)

case object StateSaved