package app.hushmessenger.patches.controls

/**
 * What differs between the Redex builds of Messenger the controls support. It's the same app
 * under different obfuscated names and a few shifted instruction positions, so each build keeps its
 * own exact hook list and the references the validators pin. Nothing is matched by count alone.
 *
 * scripts/profiles records each build, and `CompatReport.java --kotlin` turns a record into a new
 * profile. CompatProfileTest fails when a record and its profile here disagree.
 */
internal class ControlProfile(
    val hooks: Map<String, Set<String>>,
    /** Plugin gates compare their cached answer with this "not computed yet" sentinel. */
    val pluginSentinel: String,
    val preferenceGetter: String,
    /** The Notifications tab's "hide suggestions" preference key and its server-override check. */
    val peopleKey: String,
    val peopleFlagCheck: String,
    val subtabsSupplier: String,
    /** The external-browser preference key and the index of the read that loads it. */
    val browserPreferenceKey: String,
    val browserPreferenceIndex: Int,
    /** Instruction count and exits of the inbox item processor the ad filter edits. */
    val adFilterSize: Int,
    val adFilterExits: List<Int>,
)

/** 346013387, 346013440, 346013442, 346013354 and 346013394 share one mapping. */
internal val BASE_PROFILE = ControlProfile(
    hooks = expectedHooks,
    pluginSentinel = "LX/1dj;->A03:Ljava/lang/Object;",
    preferenceGetter = PREFERENCE_GETTER,
    peopleKey = "LX/JTx;->A01:LX/1BL;",
    peopleFlagCheck = "LX/16z;->A1Z(Ljava/lang/Object;J)Z",
    subtabsSupplier = "LX/2UL;->A00:Lcom/facebook/messaging/inboxsubtabs/plugins/subtabs/itemsupplier/InboxSubtabsItemSupplierImplementation;",
    browserPreferenceKey = "LX/1D1;->A1U:LX/1BK;",
    browserPreferenceIndex = 60,
    adFilterSize = 935,
    adFilterExits = listOf(916, 931),
)

/**
 * 346013370, the arm64 nodpi APK APKMirror serves as variant 19 of 580.0.0.49.91. It fills the
 * sticker keyboard's tab list inline instead of returning it. Generated from its record.
 */
internal val PROFILE_346013370 = ControlProfile(
    hooks = mapOf(
        "ads" to setOf("LX/2Wk;->D2e(LX/1fw;Lcom/google/common/collect/ImmutableList;Ljava/lang/String;)Lcom/google/common/collect/ImmutableList;"),
        "ai_fab" to setOf("LX/6ie;->render(LX/2MY;)LX/1GF;"),
        "ai_menu" to setOf("LX/HC4;->A00()Z", "LX/HC4;->A01()Z", "LX/Jdr;->A00()Z", "LX/Jdr;->A01()Z"),
        "ai_search" to setOf("LX/5OE;->A0A(LX/5OE;)Z", "LX/5OE;->A0B(LX/5OE;)Z"),
        "ai_search_chip" to setOf("LX/O7T;->render(LX/2MY;)LX/1GF;"),
        "ai_stickers" to setOf("LX/PT6;->A03(LX/PT6;)Z", "LX/PTo;->A07(LX/PTo;)Z"),
        "ai_toolbar" to setOf("LX/2aO;->A04()Z"),
        "allow_screenshot" to setOf(
            "LX/4nb;->A00(Landroid/view/Window;)V", "LX/8wJ;->onScreenCaptured()V", "LX/N1j;->run()V",
            "Lcom/facebook/screenshot/ScreenshotContentObserver;->onChange(ZLandroid/net/Uri;)V",
        ),
        "avatar_stickers" to setOf("LX/PT6;->A01(LX/PT6;)Z"),
        "avatar_tabs" to setOf("Lcom/facebook/messaging/msys/thread/composer/configuration/xapp/BaseXappComposerConfigurationFactory;->A6U(LX/5n3;)V"),
        "browser" to setOf("Lcom/facebook/messaging/browser/util/MessengerBrowserLauncher;->A0M(Landroid/net/Uri;Lcom/facebook/auth/usersession/FbUserSession;)Z"),
        "bubbles" to setOf("LX/2ZV;->A00()Z"),
        "business_suggestions" to setOf("LX/7R8;->A05(LX/7R8;)Z", "LX/7S6;->A04(LX/7S6;)Z", "LX/HCJ;->A04()Z"),
        "chat_promotions" to setOf("LX/HCH;->A0D()Z", "LX/HCH;->A0E()Z"),
        "delta_unsent" to setOf("LX/VsH;->Btd(I)Z"),
        "emoji_typeface" to setOf("LX/1KU;->A00()Landroid/graphics/Typeface;"),
        "event_prompts" to setOf("LX/HCH;->A07()Z", "LX/HCH;->A08()Z"),
        "facebook" to setOf(
            "LX/2aO;->A0C()Z", "LX/3EW;->A00()Z", "LX/3mK;->A00()Z", "LX/3mO;->A02()Z", "LX/HC2;->A02()Z",
            "LX/HMK;->A02()Z", "LX/HMk;->A06()Z", "LX/Jda;->A04()Z", "LX/Jdn;->A06()Z", "LX/Jdu;->A06()Z",
            "LX/JeC;->A00()Z", "LX/JeK;->A01()Z", "LX/JeM;->A02()Z", "LX/JeO;->A02()Z", "LX/JeT;->A03()Z",
            "LX/JeU;->A03()Z", "LX/JeW;->A01()Z", "LX/JeX;->A01()Z", "LX/JeZ;->A06()Z", "LX/Jea;->A06()Z",
            "LX/Jeb;->A06()Z",
        ),
        "friend_requests" to setOf("LX/1pl;->A09()Z", "LX/2Wk;->A02()Z"),
        "growth" to setOf("LX/1pl;->A0A()Z", "LX/2GD;->A0A(LX/2GD;)Z"),
        "hide_read_receipts" to setOf("LX/AVX;->run()V"),
        "inbox_promotions" to setOf("LX/2Ee;->A0J()Z", "LX/2Ee;->A0K()Z"),
        "keep_unsent" to setOf("LX/VTZ;->A01(Landroid/content/Intent;Lcom/facebook/auth/usersession/FbUserSession;Ljava/lang/String;)V"),
        "menu_settings" to setOf(
            "LX/HBx;->Ax3(LX/0MG;)Ljava/util/ArrayList;", "LX/Jpx;->onClick(Landroid/view/View;)V",
            "LX/NjG;->CAp(LX/4k1;I)V", "LX/WnD;->A0J(Ljava/util/List;)V",
        ),
        "moments" to setOf("LX/HC4;->A05()Z", "LX/Jdr;->A05()Z"),
        "people" to setOf("LX/1pl;->A0C()Z", "LX/2Wk;->A04()Z"),
        "people_jewel" to setOf("LX/NRn;->A01(LX/NRn;)Z"),
        "people_list_end" to setOf("LX/1pl;->A0B()Z", "LX/2Wk;->A03()Z"),
        "read_mailbox" to setOf("LX/9rH;->A01(Ljava/lang/Long;Ljava/lang/String;Ljava/lang/String;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;)V"),
        "reels_badge" to setOf("LX/7vk;->A09(LX/7vk;)Z"),
        "stories" to setOf("LX/1mh;->A00()Z"),
        "subtabs" to setOf("LX/2UK;->run()V"),
        "suggested_replies" to setOf("LX/7R8;->A06(LX/7R8;)Z", "LX/7S6;->A05(LX/7S6;)Z", "LX/HCJ;->A05()Z"),
        "typing" to setOf("LX/AgM;->run()V"),
        "typing_mailbox" to setOf("LX/8d4;->A0I(Ljava/lang/String;Z)LX/324;"),
        "unsent_indicator" to setOf("LX/VsH;->BWp(I)Ljava/lang/String;"),
    ),
    pluginSentinel = "LX/1di;->A03:Ljava/lang/Object;",
    preferenceGetter = "Lcom/facebook/prefs/shared/FbSharedPreferences;->AhF(LX/1BL;Z)Z",
    peopleKey = "LX/PKg;->A01:LX/1BM;",
    peopleFlagCheck = "LX/170;->A1Y(Ljava/lang/Object;J)Z",
    subtabsSupplier = "LX/2UK;->A00:Lcom/facebook/messaging/inboxsubtabs/plugins/subtabs/itemsupplier/InboxSubtabsItemSupplierImplementation;",
    browserPreferenceKey = "LX/1D1;->A1U:LX/1BL;",
    browserPreferenceIndex = 54,
    adFilterSize = 934,
    adFilterExits = listOf(915, 930),
)

/**
 * 346013423, the arm64 nodpi APK APKMirror serves as variant 13 of 580.0.0.49.91. Its Notifications tab
 * check loads the server flag one instruction earlier. Generated from its record.
 */
internal val PROFILE_346013423 = ControlProfile(
    hooks = mapOf(
        "ads" to setOf("LX/2Xz;->D44(LX/1gs;Lcom/google/common/collect/ImmutableList;Ljava/lang/String;)Lcom/google/common/collect/ImmutableList;"),
        "ai_fab" to setOf("LX/6kF;->render(LX/2Nf;)LX/1Gf;"),
        "ai_menu" to setOf("LX/HBB;->A00()Z", "LX/HBB;->A01()Z", "LX/JdK;->A00()Z", "LX/JdK;->A01()Z"),
        "ai_search" to setOf("LX/5RV;->A0A(LX/5RV;)Z", "LX/5RV;->A0B(LX/5RV;)Z"),
        "ai_search_chip" to setOf("LX/DB7;->render(LX/2Nf;)LX/1Gf;"),
        "ai_stickers" to setOf("LX/YAk;->A03(LX/YAk;)Z", "LX/YBZ;->A07(LX/YBZ;)Z"),
        "ai_toolbar" to setOf("LX/2bd;->A04()Z"),
        "allow_screenshot" to setOf(
            "LX/4qp;->A00(Landroid/view/Window;)V", "LX/8zQ;->onScreenCaptured()V", "LX/Pf4;->run()V",
            "Lcom/facebook/screenshot/ScreenshotContentObserver;->onChange(ZLandroid/net/Uri;)V",
        ),
        "avatar_stickers" to setOf("LX/YAk;->A01(LX/YAk;)Z"),
        "avatar_tabs" to setOf("Lcom/facebook/messaging/msys/thread/composer/configuration/xapp/BaseXappComposerConfigurationFactory;->A6U(LX/5qN;)V"),
        "browser" to setOf("Lcom/facebook/messaging/browser/util/MessengerBrowserLauncher;->A0M(Landroid/net/Uri;Lcom/facebook/auth/usersession/FbUserSession;)Z"),
        "bubbles" to setOf("LX/2ak;->A00()Z"),
        "business_suggestions" to setOf("LX/7Tb;->A05(LX/7Tb;)Z", "LX/7UZ;->A04(LX/7UZ;)Z", "LX/KHp;->A04()Z"),
        "chat_promotions" to setOf("LX/KHo;->A0D()Z", "LX/KHo;->A0E()Z"),
        "delta_unsent" to setOf("LX/YOo;->Bto(I)Z"),
        "emoji_typeface" to setOf("LX/1Ku;->A00()Landroid/graphics/Typeface;"),
        "event_prompts" to setOf("LX/KHo;->A07()Z", "LX/KHo;->A08()Z"),
        "facebook" to setOf(
            "LX/2bd;->A0C()Z", "LX/3GM;->A00()Z", "LX/3pQ;->A00()Z", "LX/3pU;->A02()Z", "LX/HB9;->A02()Z",
            "LX/HKv;->A02()Z", "LX/HL8;->A06()Z", "LX/Jcm;->A04()Z", "LX/Jcy;->A06()Z", "LX/JdN;->A06()Z",
            "LX/Jdf;->A00()Z", "LX/Jdn;->A01()Z", "LX/Jdp;->A02()Z", "LX/Jdr;->A02()Z", "LX/Jdw;->A03()Z",
            "LX/Jdx;->A03()Z", "LX/Jdz;->A01()Z", "LX/Je0;->A01()Z", "LX/Je2;->A06()Z", "LX/Je3;->A06()Z",
            "LX/Je4;->A06()Z",
        ),
        "friend_requests" to setOf("LX/1qi;->A09()Z", "LX/2Xz;->A02()Z"),
        "growth" to setOf("LX/1qi;->A0A()Z", "LX/2HI;->A0A(LX/2HI;)Z"),
        "hide_read_receipts" to setOf("LX/AZP;->run()V"),
        "inbox_promotions" to setOf("LX/2Fj;->A0J()Z", "LX/2Fj;->A0K()Z"),
        "keep_unsent" to setOf("LX/M4U;->A01(Landroid/content/Intent;Lcom/facebook/auth/usersession/FbUserSession;Ljava/lang/String;)V"),
        "menu_settings" to setOf(
            "LX/HB5;->AxC(LX/0MJ;)Ljava/util/ArrayList;", "LX/Jq9;->onClick(Landroid/view/View;)V",
            "LX/Wh6;->CB5(LX/4nF;I)V", "LX/Wh7;->A0I(Ljava/util/List;)V",
        ),
        "moments" to setOf("LX/HBB;->A05()Z", "LX/JdK;->A05()Z"),
        "people" to setOf("LX/1qi;->A0C()Z", "LX/2Xz;->A04()Z"),
        "people_jewel" to setOf("LX/H9F;->A01(LX/H9F;)Z"),
        "people_list_end" to setOf("LX/1qi;->A0B()Z", "LX/2Xz;->A03()Z"),
        "read_mailbox" to setOf("LX/9v6;->A01(Ljava/lang/Long;Ljava/lang/String;Ljava/lang/String;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;)V"),
        "reels_badge" to setOf("LX/7yl;->A09(LX/7yl;)Z"),
        "stories" to setOf("LX/1ne;->A00()Z"),
        "subtabs" to setOf("LX/2VZ;->run()V"),
        "suggested_replies" to setOf("LX/7Tb;->A06(LX/7Tb;)Z", "LX/7UZ;->A05(LX/7UZ;)Z", "LX/KHp;->A05()Z"),
        "typing" to setOf("LX/AkF;->run()V"),
        "typing_mailbox" to setOf("LX/8gA;->A0I(Ljava/lang/String;Z)LX/33W;"),
        "unsent_indicator" to setOf("LX/YOo;->BWz(I)Ljava/lang/String;"),
    ),
    pluginSentinel = "LX/1eZ;->A03:Ljava/lang/Object;",
    preferenceGetter = "Lcom/facebook/prefs/shared/FbSharedPreferences;->AhM(LX/1BV;Z)Z",
    peopleKey = "LX/JON;->A01:LX/1BW;",
    peopleFlagCheck = "LX/174;->A1Y(Ljava/lang/Object;J)Z",
    subtabsSupplier = "LX/2VZ;->A00:Lcom/facebook/messaging/inboxsubtabs/plugins/subtabs/itemsupplier/InboxSubtabsItemSupplierImplementation;",
    browserPreferenceKey = "LX/1DK;->A1U:LX/1BV;",
    browserPreferenceIndex = 54,
    adFilterSize = 935,
    adFilterExits = listOf(916, 931),
)

/** Each supported build's profile, by version code. Builds that share a mapping share a profile. */
internal val controlProfiles: Map<Int, ControlProfile> = mapOf(
    346013387 to BASE_PROFILE,
    346013440 to BASE_PROFILE,
    346013442 to BASE_PROFILE,
    346013354 to BASE_PROFILE,
    346013370 to PROFILE_346013370,
    346013394 to BASE_PROFILE,
    346013423 to PROFILE_346013423,
)

/** An unknown build gets the base profile, whose exact hooks then refuse it. */
internal fun controlProfileFor(versionCode: String?, profiles: Map<Int, ControlProfile> = controlProfiles): ControlProfile =
    versionCode?.toIntOrNull()?.let(profiles::get) ?: BASE_PROFILE

/** The profile of the APK being patched. The settings extension sets it before any control runs. */
internal var activeProfile: ControlProfile = BASE_PROFILE
