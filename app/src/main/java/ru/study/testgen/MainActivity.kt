package ru.study.testgen

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var screenInput: View
    private lateinit var screenQuiz: View
    private lateinit var screenResult: View

    private lateinit var etNotes: EditText
    private lateinit var tvCount: TextView
    private lateinit var sbCount: SeekBar

    private lateinit var tvProgress: TextView
    private lateinit var pbQuiz: ProgressBar
    private lateinit var tvQuestion: TextView
    private lateinit var optionsBox: LinearLayout
    private lateinit var tvExplain: TextView
    private lateinit var btnNext: MaterialButton

    private lateinit var tvScore: TextView
    private lateinit var tvVerdict: TextView
    private lateinit var tvMistakesTitle: TextView
    private lateinit var mistakesBox: LinearLayout

    private var questions: List<Question> = emptyList()
    private var current = 0
    private var score = 0
    private var answered = false
    private val wrongOnes = mutableListOf<Question>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        screenInput = findViewById(R.id.screenInput)
        screenQuiz = findViewById(R.id.screenQuiz)
        screenResult = findViewById(R.id.screenResult)

        etNotes = findViewById(R.id.etNotes)
        tvCount = findViewById(R.id.tvCount)
        sbCount = findViewById(R.id.sbCount)

        tvProgress = findViewById(R.id.tvProgress)
        pbQuiz = findViewById(R.id.pbQuiz)
        tvQuestion = findViewById(R.id.tvQuestion)
        optionsBox = findViewById(R.id.optionsBox)
        tvExplain = findViewById(R.id.tvExplain)
        btnNext = findViewById(R.id.btnNext)

        tvScore = findViewById(R.id.tvScore)
        tvVerdict = findViewById(R.id.tvVerdict)
        tvMistakesTitle = findViewById(R.id.tvMistakesTitle)
        mistakesBox = findViewById(R.id.mistakesBox)

        // Возвращаем текст, который был в прошлый раз
        etNotes.setText(getSharedPreferences("app", MODE_PRIVATE).getString("notes", ""))

        sbCount.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvCount.text = "Вопросов: ${progress + 3}"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        tvCount.text = "Вопросов: ${sbCount.progress + 3}"

        findViewById<MaterialButton>(R.id.btnGenerate).setOnClickListener { startTest() }
        findViewById<MaterialButton>(R.id.btnExample).setOnClickListener { etNotes.setText(SAMPLE) }
        findViewById<MaterialButton>(R.id.btnClear).setOnClickListener { etNotes.setText("") }
        findViewById<MaterialButton>(R.id.btnQuit).setOnClickListener { showScreen(screenInput) }
        findViewById<MaterialButton>(R.id.btnRetry).setOnClickListener { startTest() }
        findViewById<MaterialButton>(R.id.btnNew).setOnClickListener { showScreen(screenInput) }

        btnNext.setOnClickListener {
            if (current < questions.size - 1) {
                current++
                showQuestion()
            } else {
                showResult()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (screenInput.visibility == View.VISIBLE) {
                    finish()
                } else {
                    showScreen(screenInput)
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences("app", MODE_PRIVATE)
            .edit().putString("notes", etNotes.text.toString()).apply()
    }

    private fun showScreen(screen: View) {
        screenInput.visibility = if (screen === screenInput) View.VISIBLE else View.GONE
        screenQuiz.visibility = if (screen === screenQuiz) View.VISIBLE else View.GONE
        screenResult.visibility = if (screen === screenResult) View.VISIBLE else View.GONE
    }

    private fun startTest() {
        val text = etNotes.text.toString().trim()
        if (text.length < 80) {
            Toast.makeText(this, "Текста маловато — вставь хотя бы пару абзацев", Toast.LENGTH_LONG).show()
            return
        }
        val wanted = sbCount.progress + 3
        val result = TestGenerator.generate(text, wanted)
        if (result.size < 2) {
            Toast.makeText(
                this,
                "Не вышло найти, из чего делать вопросы. Лучше работают тексты с определениями («Термин — это …») и датами",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (result.size < wanted) {
            Toast.makeText(this, "Больше вопросов в этом тексте не нашлось: ${result.size}", Toast.LENGTH_LONG).show()
        }
        questions = result
        current = 0
        score = 0
        wrongOnes.clear()
        showScreen(screenQuiz)
        showQuestion()
    }

    private fun showQuestion() {
        val q = questions[current]
        answered = false

        tvProgress.text = "Вопрос ${current + 1} из ${questions.size}"
        pbQuiz.max = questions.size
        pbQuiz.progress = current
        tvQuestion.text = q.text
        tvExplain.visibility = View.GONE
        btnNext.isEnabled = false
        btnNext.text = if (current == questions.size - 1) "Показать результат" else "Дальше"

        optionsBox.removeAllViews()
        q.options.forEachIndexed { index, option ->
            val tv = TextView(this)
            tv.text = option
            tv.textSize = 16f
            tv.setTextColor(getColor(R.color.ink))
            tv.setBackgroundResource(R.drawable.bg_option)
            tv.setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(10)
            tv.layoutParams = lp
            tv.setOnClickListener { onAnswer(index) }
            optionsBox.addView(tv)
        }
    }

    private fun onAnswer(chosen: Int) {
        if (answered) return
        answered = true
        val q = questions[current]

        for (i in 0 until optionsBox.childCount) {
            val v = optionsBox.getChildAt(i)
            if (i == q.correct) {
                v.setBackgroundResource(R.drawable.bg_option_correct)
            } else if (i == chosen) {
                v.setBackgroundResource(R.drawable.bg_option_wrong)
            }
        }

        if (chosen == q.correct) {
            score++
        } else {
            wrongOnes.add(q)
        }

        tvExplain.text = "Из конспекта: " + q.source
        tvExplain.visibility = View.VISIBLE
        btnNext.isEnabled = true
    }

    private fun showResult() {
        val total = questions.size
        tvScore.text = "$score из $total"
        val percent = score * 100 / total
        tvVerdict.text = when {
            percent == 100 -> "Без единой ошибки. Конспект ты знаешь."
            percent >= 70 -> "Хороший результат, осталось подтянуть пару мест."
            percent >= 40 -> "Неплохо, но к конспекту стоит вернуться."
            else -> "Пока сыровато. Перечитай конспект и попробуй ещё раз."
        }

        mistakesBox.removeAllViews()
        if (wrongOnes.isEmpty()) {
            tvMistakesTitle.visibility = View.GONE
        } else {
            tvMistakesTitle.visibility = View.VISIBLE
            for (q in wrongOnes) {
                val tv = TextView(this)
                tv.text = q.text + "\n\nПравильно: " + q.options[q.correct]
                tv.textSize = 15f
                tv.setTextColor(getColor(R.color.ink))
                tv.setBackgroundResource(R.drawable.bg_card)
                tv.setPadding(dp(16), dp(14), dp(16), dp(14))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dp(10)
                tv.layoutParams = lp
                mistakesBox.addView(tv)
            }
        }
        showScreen(screenResult)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val SAMPLE =
            "Фотосинтез — это процесс преобразования энергии света в химическую энергию органических веществ. " +
            "Хлоропласты — органоиды растительной клетки, в которых происходит фотосинтез. " +
            "Хлорофилл — зелёный пигмент, который поглощает солнечный свет. " +
            "Устьица — это мелкие отверстия на поверхности листа, через которые идёт газообмен. " +
            "Транспирация — это испарение воды растением через листья. " +
            "Первые опыты по изучению фотосинтеза провёл Джозеф Пристли в 1771 году. " +
            "Около 70 процентов кислорода на Земле производят водоросли океана. " +
            "Темновая фаза фотосинтеза протекает в строме хлоропластов. " +
            "Кислород выделяется в атмосферу в процессе световой фазы фотосинтеза. " +
            "Растения поглощают из воздуха углекислый газ и выделяют кислород."
    }
}
