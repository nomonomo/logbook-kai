package logbook.notify;

/**
 * 通知の事実。届け先は文面を自分で組み立てる。
 */
public sealed interface Notification
        permits MissionReturning, NdockCompleted, CondRecovered, BadlyDamagedAlert,
        MissionConditionAlert, GimmickAchieved, MissionStarted, PortSupplyCompleted {
}
