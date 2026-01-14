package com.example.whisper_kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Language(
    val code: String,
    val englishName: String,
    val nativeName: String
)

private val allLanguages = listOf(
    Language("en", "English", "English"),
    Language("ur", "Urdu", "اردو"),
    Language("zh", "Chinese", "中文"),
    Language("de", "German", "Deutsch"),
    Language("es", "Spanish", "Español"),
    Language("ru", "Russian", "Русский"),
    Language("ko", "Korean", "한국어"),
    Language("fr", "French", "Français"),
    Language("ja", "Japanese", "日本語"),
    Language("pt", "Portuguese", "Português"),
    Language("tr", "Turkish", "Türkçe"),
    Language("pl", "Polish", "Polski"),
    Language("ca", "Catalan", "Català"),
    Language("nl", "Dutch", "Nederlands"),
    Language("ar", "Arabic", "العربية"),
    Language("sv", "Swedish", "Svenska"),
    Language("it", "Italian", "Italiano"),
    Language("id", "Indonesian", "Bahasa Indonesia"),
    Language("hi", "Hindi", "हिन्दी"),
    Language("fi", "Finnish", "Suomi"),
    Language("vi", "Vietnamese", "Tiếng Việt"),
    Language("he", "Hebrew", "עברית"),
    Language("uk", "Ukrainian", "Українська"),
    Language("el", "Greek", "Ελληνικά"),
    Language("ms", "Malay", "Bahasa Melayu"),
    Language("cs", "Czech", "Čeština"),
    Language("ro", "Romanian", "Română"),
    Language("da", "Danish", "Dansk"),
    Language("hu", "Hungarian", "Magyar"),
    Language("ta", "Tamil", "தமிழ்"),
    Language("no", "Norwegian", "Norsk"),
    Language("th", "Thai", "ไทย"),
    Language("hr", "Croatian", "Hrvatski"),
    Language("bg", "Bulgarian", "Български"),
    Language("lt", "Lithuanian", "Lietuvių"),
    Language("la", "Latin", "Latina"),
    Language("mi", "Maori", "Māori"),
    Language("ml", "Malayalam", "മലയാളം"),
    Language("cy", "Welsh", "Cymraeg"),
    Language("sk", "Slovak", "Slovenčina"),
    Language("te", "Telugu", "తెలుగు"),
    Language("fa", "Persian", "فارسی"),
    Language("lv", "Latvian", "Latviešu"),
    Language("bn", "Bengali", "বাংলা"),
    Language("sr", "Serbian", "Српски"),
    Language("az", "Azerbaijani", "Azərbaycan"),
    Language("sl", "Slovenian", "Slovenščina"),
    Language("kn", "Kannada", "ಕನ್ನಡ"),
    Language("et", "Estonian", "Eesti"),
    Language("mk", "Macedonian", "Македонски"),
    Language("br", "Breton", "Brezhoneg"),
    Language("eu", "Basque", "Euskara"),
    Language("is", "Icelandic", "Íslenska"),
    Language("hy", "Armenian", "Հայերեն"),
    Language("ne", "Nepali", "नेपाली"),
    Language("mn", "Mongolian", "Монгол"),
    Language("bs", "Bosnian", "Bosanski"),
    Language("kk", "Kazakh", "Қазақша"),
    Language("sq", "Albanian", "Shqip"),
    Language("sw", "Swahili", "Kiswahili"),
    Language("gl", "Galician", "Galego"),
    Language("mr", "Marathi", "मराठी"),
    Language("pa", "Punjabi", "ਪੰਜਾਬੀ"),
    Language("si", "Sinhala", "සිංහල"),
    Language("km", "Khmer", "ខ្មែរ"),
    Language("sn", "Shona", "ChiShona"),
    Language("yo", "Yoruba", "Yorùbá"),
    Language("so", "Somali", "Soomaali"),
    Language("af", "Afrikaans", "Afrikaans"),
    Language("oc", "Occitan", "Occitan"),
    Language("ka", "Georgian", "ქართული"),
    Language("be", "Belarusian", "Беларуская"),
    Language("tg", "Tajik", "Тоҷикӣ"),
    Language("sd", "Sindhi", "سنڌي"),
    Language("gu", "Gujarati", "ગુજરાતી"),
    Language("am", "Amharic", "አማርኛ"),
    Language("yi", "Yiddish", "ייִדיש"),
    Language("lo", "Lao", "ລາວ"),
    Language("uz", "Uzbek", "O'zbek"),
    Language("fo", "Faroese", "Føroyskt"),
    Language("ht", "Haitian Creole", "Kreyòl Ayisyen"),
    Language("ps", "Pashto", "پښتو"),
    Language("tk", "Turkmen", "Türkmen"),
    Language("nn", "Nynorsk", "Nynorsk"),
    Language("mt", "Maltese", "Malti"),
    Language("sa", "Sanskrit", "संस्कृतम्"),
    Language("lb", "Luxembourgish", "Lëtzebuergesch"),
    Language("my", "Myanmar", "မြန်မာ"),
    Language("bo", "Tibetan", "བོད་ཡིག"),
    Language("tl", "Tagalog", "Tagalog"),
    Language("mg", "Malagasy", "Malagasy"),
    Language("as", "Assamese", "অসমীয়া"),
    Language("tt", "Tatar", "Татар"),
    Language("haw", "Hawaiian", "ʻŌlelo Hawaiʻi"),
    Language("ln", "Lingala", "Lingála"),
    Language("ha", "Hausa", "Hausa"),
    Language("ba", "Bashkir", "Башҡорт"),
    Language("jw", "Javanese", "Basa Jawa"),
    Language("su", "Sundanese", "Basa Sunda")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionScreen(
    selectedLanguageCode: String,
    onLanguageSelected: (String) -> Unit,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    androidx.compose.material3.Text(
                        text = "Languages",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(allLanguages) { language ->
                LanguageItem(
                    language = language,
                    isSelected = language.code == selectedLanguageCode,
                    onClick = { onLanguageSelected(language.code) },
                    textColor = textColor
                )
                if (language != allLanguages.last()) {
                    Divider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = textColor.copy(alpha = 0.1f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageItem(
    language: Language,
    isSelected: Boolean,
    onClick: () -> Unit,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = language.englishName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
            Text(
                text = language.nativeName,
                fontSize = 13.sp,
                color = textColor.copy(alpha = 0.6f)
            )
        }
        
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
