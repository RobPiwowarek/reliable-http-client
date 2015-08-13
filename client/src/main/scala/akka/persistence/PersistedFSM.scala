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
import akka.actor._
import rhttpc._
import rhttpc.client.{SubscriptionsHolder, SubscriptionOnResponse}

trait PersistedFSM[S, D]
  extends PersistentActor
  with PersistentActorWithNotifications
  with NotificationAboutRecoveryCompleted
  with FSM[S, D]
  with SubscriptionsHolder {

  private var replyAfterSaveMsg: Option[Any] = None

  implicit class StateExt(state: State) {
    def replyingAfterSave(msg: Any = StateSaved): PersistedFSM.this.State = {
      replyAfterSaveMsg = Some(msg)
      state
    }
  }

  override def receiveRecover: Receive =
    handleSnapshotOffer orElse
      handleRecoveryCompleted


  private val handleSnapshotOffer: Receive = {
    case SnapshotOffer(metadata, snapshot) =>
      log.info(s"Recovering: $persistenceId from snapshot: $snapshot")
      val casted = snapshot.asInstanceOf[FSMState[S, D]]
      registerSubscriptions(casted.subscriptions)
      startWith(casted.state, casted.data)
  }

  onTransition {
    case (_, to) =>
      val listener = replyAfterSaveMsg.map { msg =>
        replyAfterSaveMsg = None
        new RecipientWithMsg(sender(), msg)
      }
      saveSnapshotNotifying(FSMState(to, nextStateData, subscriptions), listener)
  }

  onTermination {
    case StopEvent(Normal, _, _) =>
      deleteSnapshotsLogging()
    case StopEvent(Failure(_), _, _) =>
      deleteSnapshotsLogging()
  }

  override def receive: Receive =
    handleNotifyAboutRecoveryCompleted orElse
      handleSnapshotEvents orElse
      handleRegisterSubscription orElse
      handleMessageFromSubscription orElse
      super.receive

  override def stateChanged(): Unit = {
    saveSnapshotLogging(FSMState(stateName, stateData, subscriptions))
  }
}

case class FSMState[S, D](state: S, data: D, subscriptions: Set[SubscriptionOnResponse])