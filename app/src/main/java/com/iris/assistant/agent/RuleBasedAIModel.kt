package com.iris.assistant.agent

import com.iris.assistant.memory.ShortTermMemory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * موتور درک آفلاین گسترده IRIS.
 *
 * این نسخه خیلی وسیع‌تر شده:
 * - ده‌ها intent برای دستورات دستگاه
 * - مکالمه طبیعی‌تر و شخصیت قوی‌تر
 * - تشخیص سوال پیچیده برای ارجاع به مدل‌های بزرگ
 * - پاسخ‌های دانش ساده، جوک، انگیزشی و راهنمایی
 * - پشتیبانی بهتر از فارسی روزمره و غلط‌های املایی کوچک
 *
 * هنوز مدل زبانی واقعی نیست، اما پوشش دستورات شخصی و مکالمات روزمره
 * را به شکل قابل توجهی گسترش داده.
 */
class RuleBasedAIModel(private val context: android.content.Context? = null) : IntentModel {

    // ---------------- multi-turn slot filling ----------------

    private data class PendingSlot(val key: String, val prompt: String)

    private data class PendingIntent(
        val toolName: String,
        val filled: MutableMap<String, String>,
        val remaining: MutableList<PendingSlot>
    )

    private var pending: PendingIntent? = null

    private val cancelWords = listOf(
        "بیخیال", "بی خیال", "ولش کن", "کنسل", "فراموشش کن", "هیچی",
        "نه بی‌خیال", "لازم نیست", "دیگه نمی‌خوام", "لغو"
    )

    override suspend fun decide(userText: String, availableTools: Collection<Tool>, memory: ShortTermMemory): Decision {
        val raw = userText.trim()
        if (raw.isEmpty()) return Decision(null, emptyMap(), "بله؟ گوش می‌دم.")
        val text = normalize(raw)

        // ادامه مکالمه چندمرحله‌ای (slot filling)
        val current = pending
        if (current != null) {
            if (cancelWords.any { text.contains(it) }) {
                pending = null
                return Decision(null, emptyMap(), "باشه، بیخیالش شدم. دیگه چی کار برات بکنم؟")
            }
            val slot = current.remaining.removeAt(0)
            current.filled[slot.key] = raw
            if (current.remaining.isNotEmpty()) {
                return Decision(null, emptyMap(), current.remaining.first().prompt)
            }
            val toolName = current.toolName
            val params = current.filled.toMap()
            pending = null
            return Decision(toolName, params)
        }

        // مکالمات روزمره و شخصیت
        greetingReply(text)?.let { return Decision(null, emptyMap(), it) }

        // جستجوی بهترین intent
        val best = intents.map { it to it.score(text) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }

        if (best != null && best.second >= 2) {
            return best.first.build(raw, text)
        }

        // سوال پیچیده → سیگنال برای مدل‌های بزرگ
        if (isComplexQuestion(text)) {
            return Decision(null, emptyMap(), complexFallbacks.random())
        }

        // پاسخ‌های دانش ساده و عمومی
        knowledgeReply(text)?.let { return Decision(null, emptyMap(), it) }

        return Decision(null, emptyMap(), fallbacks.random())
    }

    // ---------------- intent definitions ----------------

    private inner class Intent(
        val triggers: List<Pair<String, Int>>,
        val build: (raw: String, text: String) -> Decision
    ) {
        fun score(text: String): Int {
            var total = 0
            for ((keyword, weight) in triggers) {
                if (fuzzyContains(text, keyword)) total += weight
            }
            return total
        }
    }

    private fun startPending(toolName: String, filled: MutableMap<String, String>, slots: List<PendingSlot>): Decision {
        pending = PendingIntent(toolName, filled, slots.toMutableList())
        return Decision(null, emptyMap(), slots.first().prompt)
    }

    private val intents: List<Intent> by lazy {
        listOf(
            // ========== آلارم و زمان ==========
            Intent(
                listOf(
                    "آلارم" to 4, "بیدارم کن" to 4, "بیدار کن" to 3, "زنگ ساعت" to 4,
                    "تنظیم ساعت" to 3, "ساعت کوک" to 3, "بیدار شو" to 2
                )
            ) { _, text ->
                val time = extractTime(text) ?: "07:00"
                Decision("set_alarm", mapOf("time" to time))
            },

            // ========== چراغ قوه ==========
            Intent(
                listOf(
                    "چراغ قوه" to 4, "چراغ‌قوه" to 4, "فلاش" to 3, "لایت" to 3,
                    "چراغ گوشی" to 3, "flashlight" to 3, "چراغ" to 1
                )
            ) { _, text ->
                val off = text.contains("خاموش") || text.contains("قطع") || text.contains("ببند")
                Decision("control_flashlight", mapOf("state" to if (off) "off" else "on"))
            },

            // ========== پیامک ==========
            Intent(
                listOf(
                    "اس ام اس" to 4, "اسمس" to 4, "پیامک" to 4, "پیام بده" to 4,
                    "پیام بفرست" to 4, "بنویس به" to 3, "بگو به" to 2, "پیام" to 1
                )
            ) { raw, _ ->
                val (contact, message) = extractSms(raw)
                when {
                    contact != null && message != null ->
                        Decision("send_sms", mapOf("contact" to contact, "message" to message))
                    contact != null ->
                        startPending("send_sms", mutableMapOf("contact" to contact),
                            listOf(PendingSlot("message", "چی بهش بگم؟")))
                    else ->
                        startPending("send_sms", mutableMapOf(),
                            listOf(
                                PendingSlot("contact", "به کی پیام بدم؟"),
                                PendingSlot("message", "چی بهش بگم؟")
                            ))
                }
            },

            // ========== تماس ==========
            Intent(
                listOf(
                    "تماس بگیر" to 4, "زنگ بزن" to 4, "تماس" to 2, "تلفن کن" to 3,
                    "کال کن" to 3, "زنگ" to 1, "تماس با" to 3
                )
            ) { raw, _ ->
                val name = extractAfter(raw, listOf("زنگ بزن به", "تماس بگیر با", "تماس با", "زنگ به", "به", "با"))
                if (!name.isNullOrBlank()) {
                    Decision("make_call", mapOf("contact" to name))
                } else {
                    startPending("make_call", mutableMapOf(), listOf(PendingSlot("contact", "با کی تماس بگیرم؟")))
                }
            },

            // ========== باز کردن اپ ==========
            Intent(
                listOf(
                    "باز کن" to 3, "بازکن" to 3, "اجرا کن" to 3, "برو تو" to 2,
                    "باز شو" to 2, "لینک کن" to 2, "بیار بالا" to 2
                )
            ) { raw, _ ->
                val app = extractAfter(raw, listOf("باز کن", "بازکن", "اجرا کن", "برو تو", "باز شو", "بیار بالا"))
                if (!app.isNullOrBlank()) {
                    Decision("open_app", mapOf("app" to app))
                } else {
                    startPending("open_app", mutableMapOf(), listOf(PendingSlot("app", "کدام اپ رو باز کنم؟")))
                }
            },

            // ========== آب و هوا ==========
            Intent(
                listOf(
                    "هوا چطوره" to 5, "آب و هوا" to 4, "آب‌وهوا" to 4, "وضعیت هوا" to 4,
                    "هوای امروز" to 4, "فردا هوا" to 3, "هوا چطور" to 4, "weather" to 3,
                    "بارون میاد" to 3, "آفتابی" to 2
                )
            ) { _, _ -> Decision("get_weather", emptyMap()) },

            // ========== موقعیت ==========
            Intent(
                listOf(
                    "کجام" to 4, "موقعیتم" to 4, "لوکیشن" to 4, "موقعیت من" to 4,
                    "کجا هستم" to 4, "کجا هستم الان" to 4, "لوکیشنم" to 3
                )
            ) { _, _ -> Decision("get_location", emptyMap()) },

            // ========== ترجمه ==========
            Intent(
                listOf("ترجمه کن" to 5, "ترجمه" to 2, "translate" to 3, "معنی‌ش چیه" to 3, "معنی اش چیه" to 3)
            ) { raw, _ ->
                val textToTranslate = extractAfter(raw, listOf("ترجمه کن", "ترجمه", "معنی‌ش چیه", "معنی اش چیه"))
                if (!textToTranslate.isNullOrBlank()) {
                    Decision("translate", mapOf("text" to textToTranslate))
                } else {
                    startPending("translate", mutableMapOf(), listOf(PendingSlot("text", "چی رو ترجمه کنم؟")))
                }
            },

            // ========== کنترل رسانه ==========
            Intent(
                listOf(
                    "آهنگ پخش کن" to 5, "موسیقی پخش کن" to 5, "پخش کن" to 2, "پلی کن" to 4,
                    "آهنگ بذار" to 4, "موزیک بذار" to 4, "شروع کن" to 1
                )
            ) { _, _ -> Decision("media_play", emptyMap()) },

            Intent(
                listOf("آهنگ بعدی" to 4, "قطعه بعدی" to 4, "بعدی" to 2, "next" to 3, "اسکیپ" to 3)
            ) { _, _ -> Decision("next_track", emptyMap()) },

            Intent(
                listOf("آهنگ قبلی" to 4, "قطعه قبلی" to 4, "قبلی" to 2, "previous" to 3)
            ) { _, _ -> Decision("previous_track", emptyMap()) },

            // ========== دسترسی‌پذیری ==========
            Intent(
                listOf("بزن روی" to 4, "کلیک روی" to 4, "کلیک کن" to 3, "لمس کن" to 3, "کلیک" to 1)
            ) { raw, _ ->
                val target = extractAfter(raw, listOf("بزن روی", "کلیک روی", "روی"))
                if (!target.isNullOrBlank()) {
                    Decision("accessibility_click", mapOf("text" to target))
                } else {
                    startPending("accessibility_click", mutableMapOf(), listOf(PendingSlot("text", "روی چی کلیک کنم؟")))
                }
            },

            Intent(
                listOf(
                    "اسکرول کن" to 4, "اسکرول" to 2, "بکش پایین" to 3, "بکش بالا" to 3,
                    "برو پایین" to 2, "برو بالا" to 2, "اسکرول پایین" to 3
                )
            ) { _, _ -> Decision("accessibility_scroll", emptyMap()) },

            // ========== ساعت و تاریخ ==========
            Intent(
                listOf(
                    "ساعت چنده" to 5, "ساعت چند است" to 5, "چند شده" to 4, "وقت چنده" to 4,
                    "ساعت چند" to 4, "time" to 3, "الان ساعت" to 4
                )
            ) { _, _ ->
                val now = SimpleDateFormat("HH:mm", Locale("fa")).format(Date())
                Decision(null, emptyMap(), "الان ساعت $now هست.")
            },

            Intent(
                listOf(
                    "امروز چندمه" to 5, "تاریخ امروز" to 5, "امروز چه روزیه" to 4,
                    "چه تاریخیه" to 4, "تاریخ" to 2, "date" to 3, "امروز چندمه"
                )
            ) { _, _ ->
                val date = SimpleDateFormat("EEEE d MMMM yyyy", Locale("fa")).format(Date())
                Decision(null, emptyMap(), "امروز $date هست.")
            },

            Intent(
                listOf("فردا چندمه" to 4, "فردا چه روزیه" to 4, "تاریخ فردا" to 4)
            ) { _, _ ->
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val date = SimpleDateFormat("EEEE d MMMM yyyy", Locale("fa")).format(cal.time)
                Decision(null, emptyMap(), "فردا $date هست.")
            },

            // ========== ماشین حساب ==========
            Intent(
                listOf(
                    "حساب کن" to 4, "چند میشه" to 4, "چند می‌شه" to 4, "محاسبه" to 3,
                    "چقدر میشه" to 3, "جمع کن" to 2, "ضرب کن" to 2
                )
            ) { raw, _ ->
                val expr = raw.replace(Regex("[^0-9+\\-*/(). ]"), "").trim()
                if (expr.isNotBlank() && expr.any { it.isDigit() }) {
                    try {
                        val result = evaluateSimple(expr)
                        Decision(null, emptyMap(), "نتیجه می‌شه: $result")
                    } catch (e: Exception) {
                        Decision(null, emptyMap(), "نتونستم این محاسبه رو انجام بدم. یه‌جور ساده‌تر بگو.")
                    }
                } else {
                    Decision(null, emptyMap(), "چی رو حساب کنم؟ مثلاً بگو «حساب کن ۲۵۰ ضربدر ۴» یا «۱۲۰ به علاوه ۸۰»")
                }
            },

            // ========== کمک و راهنمایی ==========
            Intent(
                listOf(
                    "کمک" to 3, "راهنمایی" to 4, "چه کارهایی میتونی" to 4, "چه کارهایی می‌تونی" to 4,
                    "دستورات" to 3, "لیست دستورات" to 4, "چیکار می‌کنی" to 3, "قابلیت‌هات" to 3
                )
            ) { _, _ ->
                Decision(null, emptyMap(), """
                    من آیریس هستم. این کارها رو می‌تونم برات انجام بدم:
                    
                    📞 تماس و پیامک
                    ⏰ تنظیم آلارم
                    🔦 روشن/خاموش کردن چراغ‌قوه
                    📱 باز کردن اپ‌ها
                    🎵 کنترل پخش موزیک
                    🌤 آب‌وهوا و موقعیت مکانی
                    🌐 ترجمه متن
                    🕒 گفتن ساعت و تاریخ
                    🔢 محاسبات ساده
                    
                    برای سوال‌های پیچیده و تحلیلی هم می‌تونم از مدل‌های قوی‌تر کمک بگیرم.
                    فقط بگو چی می‌خوای!
                """.trimIndent())
            }
        )
    }

    // ---------------- مکالمات روزمره و شخصیت ----------------

    private fun greetingReply(text: String): String? {
        val nickname = context?.let {
            try { com.iris.assistant.config.UserProfileStore.getNickname(it) } catch (_: Exception) { null }
        }?.takeIf { it.isNotBlank() }
        val name = if (nickname != null) " $nickname" else ""

        val howAreYou = listOf("خوبی", "چطوری", "حالت چطوره", "حالت خوبه", "خوب هستی", "حالت چه‌طوره")
        val greetings = listOf("سلام", "درود", "hi", "hello", "صبح بخیر", "ظهر بخیر", "عصر بخیر", "شب بخیر", "سلام علیکم")
        val thanks = listOf("ممنون", "مرسی", "متشکر", "دمت گرم", "ممنونم", "خیلی ممنون", "دستت درد نکنه")
        val bye = listOf("خداحافظ", "بای", "فعلا", "فعلاً", "بعدا می‌بینمت", "خدا نگه دار", "می‌رم", "بریم")
        val whoAreYou = listOf("تو کی هستی", "اسمت چیه", "کی هستی", "خودت رو معرفی کن", "چی هستی", "تو چی هستی")
        val love = listOf("دوست دارم", "عاشقتم", "خوبی تو", "قهرمانی")
        val bored = listOf("حوصله‌م سر رفته", "حوصله ندارم", "کسل شدم", "بی‌حوصله‌ام")
        val sad = listOf("غمگینم", "ناراحتم", "دلگیرم", "افسرده‌ام", "حالم بده")
        val happy = listOf("خوشحالم", "حال خوب", "عالی‌ام", "سرحال‌ام")
        val joke = listOf("جوک بگو", "یه جوک", "خنده دار بگو", "شوخی کن", "یه چیز بامزه")
        val motivate = listOf("انگیزه بده", "انرژی بده", "حالمو خوب کن", "یه حرف خوب بزن")

        return when {
            howAreYou.any { text.contains(it) } -> listOf(
                "خوبم$name، ممنون که پرسیدی! تو چطوری؟",
                "عالی‌ام$name! آماده‌ام کمکت کنم. تو چطوری؟",
                "خوب و سرحال$name. چه کمکی از دستم برمیاد؟"
            ).random()

            greetings.any { w -> text == w || text.startsWith("$w ") || text.endsWith(" $w") } -> listOf(
                "سلام$name! خوش اومدی. چه کاری برات انجام بدم؟",
                "سلام$name! آماده‌ام. بگو چی می‌خوای.",
                "درود$name! گوش می‌دم."
            ).random()

            thanks.any { text.contains(it) } -> listOf(
                "خواهش می‌کنم$name 😊 همیشه در خدمتم.",
                "قابلی نداشت$name. کار دیگه‌ای هست؟",
                "مرسی از لطفت$name. هر وقت خواستی صداش کن."
            ).random()

            bye.any { text.contains(it) } -> listOf(
                "خداحافظ$name! مواظب خودت باش.",
                "فعلاً$name! هر وقت برگشتی اینجام.",
                "خدا نگهت داره$name. بعداً می‌بینمت."
            ).random()

            whoAreYou.any { text.contains(it) } ->
                "من آیریس هستم، دستیار صوتی شخصی تو. کارهای ساده‌ات رو خودم سریع انجام می‌دم و برای سوال‌های سخت‌تر از مدل‌های قوی‌تر کمک می‌گیرم. همیشه اینجام$name."

            love.any { text.contains(it) } -> listOf(
                "منم ازت خوشم میاد$name 😊",
                "قربونت$name! منم اینجام برات.",
                "خوشحالم که راضی هستی$name."
            ).random()

            bored.any { text.contains(it) } -> listOf(
                "حوصله‌ت سر رفته؟ بگو یه جوک برات بگم یا کاری برات انجام بدم.",
                "بیا یه کاری کنیم$name. می‌خوای آهنگ پخش کنم یا آلارم تنظیم کنم؟",
                "کسل شدی؟ من اینجام. بگو چی حالتو بهتر می‌کنه."
            ).random()

            sad.any { text.contains(it) } -> listOf(
                "متأسفم که حالت خوب نیست$name. من اینجام اگر خواستی حرف بزنی یا کمکی بخواد.",
                "امیدوارم زودتر حالت بهتر بشه. اگر کاری از دستم برمیاد بگو.",
                "همه گاهی حالشون بده. من کنارت‌ام$name."
            ).random()

            happy.any { text.contains(it) } -> listOf(
                "خوشحالم که حالت خوبه$name! 😊",
                "عالی! انرژی‌ات رو حفظ کن.",
                "چه خوب$name! این حس رو ادامه بده."
            ).random()

            joke.any { text.contains(it) } -> jokes.random()

            motivate.any { text.contains(it) } -> motivations.random()

            else -> null
        }
    }

    private val jokes = listOf(
        "چرا کامپیوتر به دکتر رفت؟ چون ویروس گرفته بود! 😄",
        "یه برنامه‌نویس رفت رستوران. گفت: «یه کامنت بدون کد لطفاً!»",
        "چرا پایتون از بقیه زبان‌ها محبوب‌تره؟ چون هیچ‌وقت exception نمی‌ده... دروغه، زیاد می‌ده 😂",
        "معلم پرسید: «آینده‌ات چیه؟» دانش‌آموز گفت: «۴۰۴ Not Found!»",
        "یه ربات عاشق شد. بهش گفتن احساس نداری. گفت: «پس این ارور قلبی چیه؟»"
    )

    private val motivations = listOf(
        "هر روز یه قدم کوچیک هم پیشرفتی. ادامه بده!",
        "تو قوی‌تر از چیزی هستی که فکر می‌کنی. باور داشته باش.",
        "اشتباهات بخشی از مسیرن. مهم اینه که بلند شی و ادامه بدی.",
        "امروز بهترین زمان برای شروع یه چیز جدیده.",
        "موفقیت یه‌شبه نمیاد، ولی هر روز می‌تونی بهش نزدیک‌تر شی."
    )

    // ---------------- دانش ساده آفلاین ----------------

    private fun knowledgeReply(text: String): String? {
        return when {
            text.contains("پایتخت ایران") || text.contains("پایتخت تهران") ->
                "پایتخت ایران تهران است."

            text.contains("رئیس جمهور") && text.contains("ایران") ->
                "من اطلاعات لحظه‌ای ندارم. برای اطلاعات به‌روز بهتره از مدل‌های آنلاین استفاده کنیم."

            text.contains("چند تا سیاره") || text.contains("سیاره‌های منظومه") ->
                "منظومه شمسی ۸ سیاره داره: عطارد، زهره، زمین، مریخ، مشتری، زحل، اورانوس و نپتون."

            text.contains("بزرگ‌ترین اقیانوس") || text.contains("بزرگترین اقیانوس") ->
                "اقیانوس آرام بزرگ‌ترین اقیانوس جهانه."

            text.contains("زبان برنامه نویسی") && (text.contains("بهتر") || text.contains("یاد بگیرم")) ->
                "بستگی به هدفت داره. برای شروع خیلی‌ها پایتون رو پیشنهاد می‌کنن چون ساده‌ست. برای اندروید هم کاتلین عالیه."

            text.contains("هوش مصنوعی چیه") || text.contains("ai چیه") ->
                "هوش مصنوعی یعنی سیستم‌هایی که می‌تونن کارهایی شبیه انسان انجام بدن؛ مثل فهمیدن زبان، تشخیص تصویر یا تصمیم‌گیری. من خودم یه نمونه‌ش هستم!"

            else -> null
        }
    }

    /** تشخیص سوال پیچیده برای ارجاع به مدل‌های بزرگ */
    fun isComplexQuestion(text: String): Boolean {
        val complexKeywords = listOf(
            "تحلیل", "استراتژی", "برنامه ریزی", "برنامه‌ریزی", "طراحی کن", "پیشنهاد بده",
            "مقایسه کن", "توضیح کامل", "چرا باید", "چطور می‌تونم", "چگونه می‌توانم",
            "راهکار", "ایده بده", "نقطه ضعف", "نقطه قوت", "آینده", "پیش‌بینی",
            "بهینه سازی", "بهینه‌سازی", "بهترین روش", "تحقیق کن", "مقاله", "کد بنویس",
            "الگوریتم", "معماری سیستم", "کسب و کار", "استارتاپ", "سرمایه گذاری",
            "سرمایه‌گذاری", "بررسی کن", "گزارش بده", "خلاصه کن", "نقد کن",
            "مزایا و معایب", "جدول مقایسه", "مرحله به مرحله", "گام به گام"
        )
        val longQuestion = text.split(" ").size >= 9
        val hasQuestionMark = text.contains("؟") || text.contains("?")
        return complexKeywords.any { text.contains(it) } || (longQuestion && hasQuestionMark)
    }

    private val fallbacks = listOf(
        "متوجه نشدم. می‌تونی یه‌جور دیگه بگی؟ مثلاً «زنگ بزن به علی» یا «چراغ قوه رو روشن کن»",
        "این یکی رو دقیق نفهمیدم. امتحان کن بگو «به مامان بگو دیر می‌رسم» یا «آلارم ساعت ۷»",
        "من هنوز این دستور رو بلد نیستم. می‌تونی مثل این بگی: «اینستاگرام رو باز کن» یا «آهنگ پخش کن»",
        "ببخشید، متوجه منظورت نشدم. یه کم ساده‌تر یا واضح‌تر بگو تا کمکت کنم."
    )

    private val complexFallbacks = listOf(
        "این سوال کمی پیچیده‌ست. بذار از مدل‌های قوی‌تر کمک بگیرم...",
        "برای این مورد نیاز به تحلیل بیشتری دارم. دارم از منابع قوی‌تر می‌پرسم...",
        "سوال خوب و عمیقیه. اجازه بده دقیق‌تر بررسی کنم با مدل‌های بزرگ‌تر...",
        "این یکی از دست منِ آفلاین خارجِه. دارم وصل می‌شم به مدل‌های قوی‌تر..."
    )

    // ---------------- helpers ----------------

    private fun evaluateSimple(expr: String): String {
        val cleaned = expr.replace(" ", "")
            .replace("×", "*").replace("÷", "/")
            .replace("x", "*").replace("X", "*")
        // تبدیل اعداد فارسی
        val ascii = cleaned.map { digitMap[it] ?: it }.joinToString("")
        val parts = ascii.split(Regex("(?<=[0-9.])(?=[+\\-*/])|(?<=[+\\-*/])(?=[0-9.])"))
        if (parts.size < 3) throw IllegalArgumentException("too simple")
        var result = parts[0].toDouble()
        var i = 1
        while (i < parts.size - 1) {
            val op = parts[i]
            val num = parts[i + 1].toDouble()
            result = when (op) {
                "+" -> result + num
                "-" -> result - num
                "*" -> result * num
                "/" -> if (num == 0.0) throw ArithmeticException("division by zero") else result / num
                else -> throw IllegalArgumentException("unknown op")
            }
            i += 2
        }
        return if (result == result.toLong().toDouble()) result.toLong().toString()
        else "%.2f".format(result)
    }

    private val digitMap = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )

    private fun normalize(input: String): String {
        var s = input.map { digitMap[it] ?: it }.joinToString("")
        s = s.replace('ي', 'ی').replace('ك', 'ک')
        s = s.replace(Regex("[\u064B-\u0652\u0670]"), "")
        s = s.replace('\u200c', ' ')
        s = s.replace(Regex("[،,.!؟?؛;:\"'()\\[\\]{}]"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        return s.lowercase()
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }

    private fun fuzzyContains(text: String, keyword: String): Boolean {
        val k = normalize(keyword)
        if (k.contains(" ")) return text.contains(k)
        if (text.contains(k)) return true
        if (k.length < 4) return false
        return text.split(" ").any { w ->
            w.length in (k.length - 2)..(k.length + 2) && levenshtein(w, k) <= 1
        }
    }

    private fun cleanWord(w: String): String =
        w.trim().trim('،', ',', '.', '!', '؟', '?', '؛', ';', ':', '"', '\'', '(', ')', '[', ']', '{', '}')

    private fun extractAfter(raw: String, keywords: List<String>): String? {
        val words = raw.trim().split(" ").map { cleanWord(it) }.filter { it.isNotBlank() }
        for (kw in keywords.sortedByDescending { it.length }) {
            val kwWords = kw.split(" ")
            if (kwWords.size > words.size) continue
            for (i in 0..(words.size - kwWords.size)) {
                if (words.subList(i, i + kwWords.size).map { it.lowercase() } == kwWords.map { it.lowercase() }) {
                    val rest = words.subList(i + kwWords.size, words.size)
                    val value = rest.firstOrNull { it.lowercase() !in FILLER_WORDS }
                    if (!value.isNullOrBlank()) return value
                }
            }
        }
        return null
    }

    private fun extractSms(raw: String): Pair<String?, String?> {
        val patterns = listOf(
            Regex("""به\s+(\S+)\s+بگو\s+(.+)"""),
            Regex("""به\s+(\S+)\s+بنویس\s+(.+)"""),
            Regex("""پیام\s+(?:بده\s+)?به\s+(\S+)\s+(?:که\s+)?(.+)"""),
            Regex("""(?:اس[\s‌]?ام[\s‌]?اس|پیامک)\s+به\s+(\S+)\s+(.+)""")
        )
        for (p in patterns) {
            val m = p.find(raw)
            if (m != null) return cleanWord(m.groupValues[1]) to m.groupValues[2].trim()
        }
        val contactOnly = listOf(
            Regex("""به\s+(\S+)\s+پیام"""),
            Regex("""پیام\s+(?:بده\s+)?به\s+(\S+)""")
        )
        for (p in contactOnly) {
            val m = p.find(raw)
            if (m != null) return cleanWord(m.groupValues[1]) to null
        }
        return null to null
    }

    private fun extractTime(text: String): String? {
        val hm = Regex("""(\d{1,2})[:.](\d{2})""").find(text)
        if (hm != null) return "${hm.groupValues[1].padStart(2, '0')}:${hm.groupValues[2]}"
        val hourOnly = Regex("""ساعت\s*(\d{1,2})""").find(text)
        if (hourOnly != null) return "${hourOnly.groupValues[1].padStart(2, '0')}:00"
        // پشتیبانی از «هفت صبح» و مشابه (ساده)
        val persianHours = mapOf(
            "یک" to 1, "دو" to 2, "سه" to 3, "چهار" to 4, "پنج" to 5,
            "شش" to 6, "هفت" to 7, "هشت" to 8, "نه" to 9, "ده" to 10,
            "یازده" to 11, "دوازده" to 12
        )
        for ((word, h) in persianHours) {
            if (text.contains(word)) {
                val hour = if (text.contains("صبح") || text.contains("ق.ظ")) h
                else if (text.contains("شب") || text.contains("عصر") || text.contains("ب.ظ")) (h % 12) + 12
                else h
                return "%02d:00".format(hour)
            }
        }
        return null
    }

    companion object {
        private val FILLER_WORDS = listOf(
            "رو", "را", "به", "با", "کن", "بزن", "بگو", "لطفا", "لطفاً",
            "برام", "میشه", "می‌شه", "واسم", "دیگه", "الان", "یه", "یک"
        )
    }
}
