module logbook {
    requires static lombok;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires javafx.media;
    requires javafx.swing;
    requires javafx.web;
    requires jdk.jsobject;
    requires org.eclipse.jetty.ee10.servlet;
    requires org.eclipse.jetty.http;
    requires org.slf4j;
    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;
    requires jdk.crypto.ec;
    
    requires transitive com.fasterxml.jackson.annotation;
    requires transitive jakarta.json;
    requires transitive jakarta.servlet;
    requires transitive java.desktop;
    requires transitive java.scripting;
    requires transitive javafx.base;
    requires transitive javafx.controls;
    requires transitive javafx.fxml;
    requires transitive javafx.graphics;
    requires transitive org.controlsfx.controls;
    requires transitive org.eclipse.jetty.client;
    requires transitive org.eclipse.jetty.ee10.proxy;
    requires transitive org.eclipse.jetty.io;
    requires transitive org.eclipse.jetty.server;
    requires transitive org.eclipse.jetty.util;

    exports logbook;
    exports logbook.api;
    exports logbook.bean;
    exports logbook.internal;
    exports logbook.internal.gui;
    exports logbook.internal.log;
    exports logbook.internal.proxy;
    exports logbook.plugin;
    exports logbook.plugin.gui;
    exports logbook.plugin.lifecycle;
    exports logbook.proxy;
    exports org.apache.logging.log4j;

    provides logbook.api.APIListenerSpi with
        logbook.api.ApiGetMemberBasic,
        logbook.api.ApiGetMemberDeck,
        logbook.api.ApiGetMemberKdock,
        logbook.api.ApiGetMemberMapinfo,
        logbook.api.ApiGetMemberMaterial,
        logbook.api.ApiGetMemberNdock,
        logbook.api.ApiGetMemberPresetDeck,
        logbook.api.ApiGetMemberQuestlist,
        logbook.api.ApiGetMemberRequireInfo,
        logbook.api.ApiGetMemberShip2,
        logbook.api.ApiGetMemberShip3,
        logbook.api.ApiGetMemberShipDeck,
        logbook.api.ApiGetMemberSlotItem,
        logbook.api.ApiGetMemberUseitem,
        logbook.api.ApiPortPort,
        logbook.api.ApiReqAirCorpsSetAction,
        logbook.api.ApiReqAirCorpsSetPlane,
        logbook.api.ApiReqAirCorpsSupply,
        logbook.api.ApiReqBattleMidnightBattle,
        logbook.api.ApiReqBattleMidnightSpMidnight,
        logbook.api.ApiReqCombinedBattleAirbattle,
        logbook.api.ApiReqCombinedBattleBattle,
        logbook.api.ApiReqCombinedBattleBattleWater,
        logbook.api.ApiReqCombinedBattleBattleresult,
        logbook.api.ApiReqCombinedBattleEachBattle,
        logbook.api.ApiReqCombinedBattleEcBattle,
        logbook.api.ApiReqCombinedBattleEcMidnightBattle,
        logbook.api.ApiReqCombinedBattleEcNightToDay,
        logbook.api.ApiReqCombinedBattleGobackPort,
        logbook.api.ApiReqCombinedBattleLdAirbattle,
        logbook.api.ApiReqCombinedBattleMidnightBattle,
        logbook.api.ApiReqCombinedBattleSpMidnight,
        logbook.api.ApiReqHenseiChange,
        logbook.api.ApiReqHenseiCombined,
        logbook.api.ApiReqHenseiPresetSelect,
        logbook.api.ApiReqHokyuCharge,
        logbook.api.ApiReqKaisouMarriage,
        logbook.api.ApiReqKaisouPowerup,
        logbook.api.ApiReqKaisouSlotDeprive,
        logbook.api.ApiReqKaisouSlotExchangeIndex,
        logbook.api.ApiReqKousyouCreateitem,
        logbook.api.ApiReqKousyouCreateship,
        logbook.api.ApiReqKousyouCreateshipSpeedchange,
        logbook.api.ApiReqKousyouDestroyitem2,
        logbook.api.ApiReqKousyouDestroyship,
        logbook.api.ApiReqKousyouGetship,
        logbook.api.ApiReqKousyouRemodelSlot,
        logbook.api.ApiReqKousyouRemodelSlotlist,
        logbook.api.ApiReqMapAnchorageRepair,
        logbook.api.ApiReqMapNext,
        logbook.api.ApiReqMapStart,
        logbook.api.ApiReqMemberItemuse,
        logbook.api.ApiReqMissionResult,
        logbook.api.ApiReqMissionStart,
        logbook.api.ApiReqNyukyoSpeedchange,
        logbook.api.ApiReqNyukyoStart,
        logbook.api.ApiReqPracticeBattle,
        logbook.api.ApiReqPracticeBattleresult,
        logbook.api.ApiReqPracticeMidnightBattle,
        logbook.api.ApiReqQuestClearitemget,
        logbook.api.ApiReqQuestStop,
        logbook.api.ApiReqSortieAirbattle,
        logbook.api.ApiReqSortieBattle,
        logbook.api.ApiReqSortieBattleresult,
        logbook.api.ApiReqSortieLdAirbattle,
        logbook.api.ApiReqSortieLdShooting,
        logbook.api.ApiStart2;
    provides logbook.plugin.lifecycle.StartUp with
        logbook.internal.CheckUpdateStartUp;
    provides logbook.proxy.ContentListenerSpi with
        logbook.internal.APIListener,
        logbook.internal.ImageListener;
    provides logbook.proxy.ProxyServerSpi with
        logbook.internal.proxy.ProxyServerImpl;

    opens logbook;
    opens logbook.api;
    opens logbook.bean;
    opens logbook.internal;
    opens logbook.internal.gui;
    opens logbook.internal.log;
    opens logbook.internal.proxy;
    opens logbook.plugin;
    opens logbook.plugin.gui;
    opens logbook.plugin.lifecycle;
    opens logbook.proxy;
    opens logbook.gui;
    opens logbook.bouyomi;
    opens logbook.capture_options;
    opens logbook.map;
    opens logbook.mission;
    opens logbook.quest;
    opens logbook.supplemental;
    
    
    uses logbook.api.APIListenerSpi;
    uses logbook.plugin.lifecycle.StartUp;
    uses logbook.plugin.gui.FleetTabRemark;
    uses logbook.proxy.ContentListenerSpi;
    uses logbook.proxy.ProxyServerSpi;

    uses logbook.plugin.gui.MainCommandMenu;
    uses logbook.plugin.gui.MainCalcMenu;
    uses logbook.plugin.gui.MainExtMenu;
}
