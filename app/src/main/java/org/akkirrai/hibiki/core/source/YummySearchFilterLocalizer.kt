package org.akkirrai.hibiki.core.source

import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.SearchFilterOption

internal object YummySearchFilterLocalizer {
    fun localize(
        catalog: AnimeSearchFilterCatalog,
        preferEnglish: Boolean,
    ): AnimeSearchFilterCatalog {
        return AnimeSearchFilterCatalog(
            sortOptions = catalog.sortOptions.map { it.localize(sortLabels, preferEnglish) },
            typeOptions = catalog.typeOptions.map { it.localize(typeLabels, preferEnglish) },
            statusOptions = catalog.statusOptions.map { it.localize(statusLabels, preferEnglish) },
            genreOptions = catalog.genreOptions.map { it.localize(genreLabels, preferEnglish) },
            capabilities = catalog.capabilities,
        )
    }

    private fun SearchFilterOption.localize(
        dictionary: Map<String, LocalizedLabel>,
        preferEnglish: Boolean,
    ): SearchFilterOption {
        val labels = dictionary[id]
        val localizedTitle = when {
            labels == null -> fallbackLabel(id, preferEnglish)
            preferEnglish -> labels.en
            else -> labels.ru
        }
        return copy(title = localizedTitle)
    }

    private fun fallbackLabel(alias: String, preferEnglish: Boolean): String {
        if (!preferEnglish) return alias
        return alias
            .replace('-', ' ')
            .replace('_', ' ')
            .split(' ')
            .filter(String::isNotBlank)
            .joinToString(" ") { part ->
                part.replaceFirstChar { it.uppercase() }
            }
    }

    private data class LocalizedLabel(
        val ru: String,
        val en: String,
    )

    private val sortLabels = mapOf(
        "relevance" to LocalizedLabel("Релевантности", "Relevance"),
        "top" to LocalizedLabel("Рейтингу", "Rating"),
        "title" to LocalizedLabel("Названию", "Title"),
        "year" to LocalizedLabel("Дате выхода", "Release date"),
        "rating" to LocalizedLabel("Рейтингу", "Rating"),
        "rating_counters" to LocalizedLabel("Количеству оценок", "Rating count"),
        "votes" to LocalizedLabel("Голосам", "Votes"),
        "views" to LocalizedLabel("Просмотрам", "Views"),
        "comments" to LocalizedLabel("Комментариям", "Comments"),
        "random" to LocalizedLabel("Случайно", "Random"),
        "id" to LocalizedLabel("Сначала новые", "Newest added"),
    )

    private val typeLabels = mapOf(
        "tv" to LocalizedLabel("Сериал", "Series"),
        "movie" to LocalizedLabel("Полнометражный фильм", "Feature film"),
        "short_movie" to LocalizedLabel("Короткометражный фильм", "Short film"),
        "ova" to LocalizedLabel("OVA", "OVA"),
        "special" to LocalizedLabel("Спэшл", "Special"),
        "short_serial" to LocalizedLabel("Малометражный сериал", "Short series"),
        "ona" to LocalizedLabel("ONA", "ONA"),
    )

    private val statusLabels = mapOf(
        "released" to LocalizedLabel("Вышел", "Released"),
        "ongoing" to LocalizedLabel("Онгоинг", "Ongoing"),
        "announcement" to LocalizedLabel("Анонс", "Announcement"),
    )

    private val genreLabels = mapOf(
        "al-ternativnaya-istoriya" to LocalizedLabel("Альтернативная история", "Alternate history"),
        "al-ternativnaya-real-nost" to LocalizedLabel("Альтернативная реальность", "Alternate reality"),
        "angely" to LocalizedLabel("Ангелы", "Angels"),
        "androidy" to LocalizedLabel("Андроиды", "Androids"),
        "antivojna" to LocalizedLabel("Антивойна", "Anti-war"),
        "antiutopiya" to LocalizedLabel("Антиутопия", "Dystopia"),
        "basketbol" to LocalizedLabel("Баскетбол", "Basketball"),
        "bezumie" to LocalizedLabel("Безумие", "Madness"),
        "bisenen" to LocalizedLabel("Бисёнэн", "Bishonen"),
        "boevye-iskusstva" to LocalizedLabel("Боевые искусства", "Martial arts"),
        "bogi" to LocalizedLabel("Божества", "Deities"),
        "vampiry" to LocalizedLabel("Вампиры", "Vampires"),
        "ved-my" to LocalizedLabel("Ведьмы", "Witches"),
        "vestern" to LocalizedLabel("Вестерн", "Western"),
        "virtual-naya-real-nost" to LocalizedLabel("Виртуальная реальность", "Virtual reality"),
        "voennaya-tematika" to LocalizedLabel("Военная тематика", "Military theme"),
        "vojna" to LocalizedLabel("Война", "War"),
        "vori" to LocalizedLabel("Воры", "Thieves"),
        "garem" to LocalizedLabel("Гарем", "Harem"),
        "garem-dlya-devochek" to LocalizedLabel("Гарем для девочек", "Reverse harem"),
        "trap" to LocalizedLabel("Гендерная интрига", "Gender bender"),
        "demony" to LocalizedLabel("Демоны", "Demons"),
        "detektiv" to LocalizedLabel("Детектив", "Detective"),
        "dzesej" to LocalizedLabel("Дзёсэй", "Josei"),
        "drakony" to LocalizedLabel("Драконы", "Dragons"),
        "drama" to LocalizedLabel("Драма", "Drama"),
        "zombi" to LocalizedLabel("Зомби", "Zombies"),
        "igry" to LocalizedLabel("Игры", "Games"),
        "inoplanetyane" to LocalizedLabel("Инопланетные расы", "Alien races"),
        "ii" to LocalizedLabel("Искусственный интеллект", "Artificial intelligence"),
        "iskusstvo" to LocalizedLabel("Искусство", "Art"),
        "istoricheskij" to LocalizedLabel("Исторический", "Historical"),
        "isekai" to LocalizedLabel("Исэкай", "Isekai"),
        "kiberpank" to LocalizedLabel("Киберпанк", "Cyberpunk"),
        "kiborgi" to LocalizedLabel("Киборги", "Cyborgs"),
        "chinese3d" to LocalizedLabel("Китайское 3D", "Chinese 3D"),
        "komediya" to LocalizedLabel("Комедия", "Comedy"),
        "kosmicheskie-priklyucheniya" to LocalizedLabel("Космос", "Space"),
        "kulinariya" to LocalizedLabel("Кулинария", "Cooking"),
        "lolikon" to LocalizedLabel("Лоликон", "Lolicon"),
        "lyubovnyj-treugol-nik" to LocalizedLabel("Любовный треугольник", "Love triangle"),
        "magiya" to LocalizedLabel("Магия", "Magic"),
        "manga" to LocalizedLabel("Манга", "Manga"),
        "mafiya-yakudza" to LocalizedLabel("Мафия/Якудза", "Mafia / Yakuza"),
        "maho-sedze" to LocalizedLabel("Махо-сёдзё", "Magical girl"),
        "meha" to LocalizedLabel("Меха", "Mecha"),
        "mistika" to LocalizedLabel("Мистика", "Mysticism"),
        "motorcycles" to LocalizedLabel("Мотоциклы", "Motorcycles"),
        "muzyka" to LocalizedLabel("Музыка", "Music"),
        "nelinejnyj-syuzhet" to LocalizedLabel("Нелинейный сюжет", "Nonlinear plot"),
        "ne-yaponskoe" to LocalizedLabel("Не японское", "Non-Japanese"),
        "nindzya" to LocalizedLabel("Ниндзя", "Ninja"),
        "ohotniki-za-golovami" to LocalizedLabel("Охотники за головами", "Bounty hunters"),
        "parallel-nyj-mir" to LocalizedLabel("Параллельный мир", "Parallel world"),
        "parodiya" to LocalizedLabel("Пародия", "Parody"),
        "perestrelki" to LocalizedLabel("Перестрелки", "Gunfights"),
        "pilotiruemye-roboty" to LocalizedLabel("Пилотируемые роботы", "Piloted robots"),
        "piraty" to LocalizedLabel("Пираты", "Pirates"),
        "povsednevnost" to LocalizedLabel("Повседневность", "Slice of life"),
        "politika" to LocalizedLabel("Политика", "Politics"),
        "policejskie" to LocalizedLabel("Полицейские", "Police"),
        "lyudi-zveri" to LocalizedLabel("Полулюди", "Humanoids"),
        "postapokaliptika" to LocalizedLabel("Постапокалиптика", "Post-apocalyptic"),
        "prestupnyj-mir" to LocalizedLabel("Преступный мир", "Crime world"),
        "prizraki" to LocalizedLabel("Призраки", "Ghosts"),
        "priklyucheniya" to LocalizedLabel("Приключения", "Adventure"),
        "proksi-boi" to LocalizedLabel("Прокси бои", "Proxy battles"),
        "psihologiya" to LocalizedLabel("Психология", "Psychology"),
        "puteshestviya-vo-vremeni" to LocalizedLabel("Путешествия во времени", "Time travel"),
        "romantika" to LocalizedLabel("Романтика", "Romance"),
        "rysalki" to LocalizedLabel("Русалки", "Mermaids"),
        "rossiya-v-anime" to LocalizedLabel("Русские в аниме", "Russians in anime"),
        "samurai" to LocalizedLabel("Самураи", "Samurai"),
        "sverh-estestvennoe" to LocalizedLabel("Сверхъестественное", "Supernatural"),
        "sedze" to LocalizedLabel("Сёдзё", "Shojo"),
        "sedze-aj" to LocalizedLabel("Сёдзё-ай", "Shojo-ai"),
        "senen" to LocalizedLabel("Сёнэн", "Shonen"),
        "senen-aj" to LocalizedLabel("Сёнэн-ай", "Shonen-ai"),
        "silovye-kostyumy" to LocalizedLabel("Силовые костюмы", "Powered suits"),
        "sovremennoe-fentezi" to LocalizedLabel("Современное фэнтези", "Modern fantasy"),
        "sport" to LocalizedLabel("Спорт", "Sports"),
        "srazheniya-na-mechah" to LocalizedLabel("Сражения на мечах", "Sword fights"),
        "stimpank" to LocalizedLabel("Стимпанк", "Steampunk"),
        "sukkuby" to LocalizedLabel("Суккубы", "Succubi"),
        "supersposobnosti" to LocalizedLabel("Суперспособности", "Superpowers"),
        "sejnen" to LocalizedLabel("Сэйнэн", "Seinen"),
        "tajnyj-zagovor" to LocalizedLabel("Тайный заговор", "Conspiracy"),
        "temnoe-fentezi" to LocalizedLabel("Тёмное фэнтези", "Dark fantasy"),
        "temnye-el-fy" to LocalizedLabel("Тёмные эльфы", "Dark elves"),
        "terroristy" to LocalizedLabel("Террористы", "Terrorists"),
        "transformery" to LocalizedLabel("Трансформеры", "Transformers"),
        "triller" to LocalizedLabel("Триллер", "Thriller"),
        "ubijcy" to LocalizedLabel("Убийцы", "Assassins"),
        "ugasy" to LocalizedLabel("Ужасы", "Horror"),
        "fantastika" to LocalizedLabel("Фантастика", "Sci-fi"),
        "fei" to LocalizedLabel("Феи", "Fairies"),
        "fentezi" to LocalizedLabel("Фэнтези", "Fantasy"),
        "badguys" to LocalizedLabel("Хулиганы", "Delinquents"),
        "celyj-fentezi-mir" to LocalizedLabel("Целый фэнтези мир", "Whole fantasy world"),
        "shkola" to LocalizedLabel("Школьная жизнь", "School life"),
        "ekshen" to LocalizedLabel("Экшен", "Action"),
        "el-fy" to LocalizedLabel("Эльфы", "Elves"),
        "erotica" to LocalizedLabel("Эротика", "Erotica"),
        "etti" to LocalizedLabel("Этти", "Ecchi"),
    )
}
