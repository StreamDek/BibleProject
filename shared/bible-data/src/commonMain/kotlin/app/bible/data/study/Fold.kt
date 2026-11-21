package app.bible.data.study

/**
 * Folds text for accent-insensitive matching across English, transliterations, Greek and Hebrew: lower case,
 * no accents, breathings or vowel points, final sigma as sigma, and transliteration marks (ʼ ʽ . -) removed.
 * The pipeline folds the index with this same function, so the app and the index always agree.
 */
object Fold {
    // Generated from Unicode NFD: each precomposed Latin or Greek letter and its unaccented lower-case base.
    private const val FROM = "ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞàáâãäåçèéêëìíîïñòóôõöùúûüýÿĀāĂăĄąĆćĈĉĊċČčĎďĐĒēĔĕĖėĘęĚěĜĝĞğĠġĢģĤĥĦĨĩĪīĬĭĮįİĲĴĵĶķĹĺĻļĽľĿŁŃńŅņŇňŊŌōŎŏŐőŒŔŕŖŗŘřŚśŜŝŞşŠšŢţŤťŦŨũŪūŬŭŮůŰűŲųŴŵŶŷŸŹźŻżŽžƁƂƄƆƇƉƊƋƎƏƐƑƓƔƖƗƘƜƝƟƠơƢƤƦƧƩƬƮƯưƱƲƳƵƷƸƼǄǅǇǈǊǋǍǎǏǐǑǒǓǔǕǖǗǘǙǚǛǜǞǟǠǡǢǣǤǦǧǨǩǪǫǬǭǮǯǰǱǲǴǵǶǷǸǹǺǻǼǽǾǿȀȁȂȃȄȅȆȇȈȉȊȋȌȍȎȏȐȑȒȓȔȕȖȗȘșȚțȜȞȟȠȢȤȦȧȨȩȪȫȬȭȮȯȰȱȲȳȺȻȽȾɁɃɄɅɆɈɊɌɎḀḁḂḃḄḅḆḇḈḉḊḋḌḍḎḏḐḑḒḓḔḕḖḗḘḙḚḛḜḝḞḟḠḡḢḣḤḥḦḧḨḩḪḫḬḭḮḯḰḱḲḳḴḵḶḷḸḹḺḻḼḽḾḿṀṁṂṃṄṅṆṇṈṉṊṋṌṍṎṏṐṑṒṓṔṕṖṗṘṙṚṛṜṝṞṟṠṡṢṣṤṥṦṧṨṩṪṫṬṭṮṯṰṱṲṳṴṵṶṷṸṹṺṻṼṽṾṿẀẁẂẃẄẅẆẇẈẉẊẋẌẍẎẏẐẑẒẓẔẕẖẗẘẙẛẞẠạẢảẤấẦầẨẩẪẫẬậẮắẰằẲẳẴẵẶặẸẹẺẻẼẽẾếỀềỂểỄễỆệỈỉỊịỌọỎỏỐốỒồỔổỖỗỘộỚớỜờỞởỠỡỢợỤụỦủỨứỪừỬửỮữỰựỲỳỴỵỶỷỸỹỺỼỾͰͲʹͶ;Ϳ΅Ά·ΈΉΊΌΎΏΐΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩΪΫάέήίΰϊϋόύώϏϓϔϘϚϜϞϠϢϤϦϨϪϬϮϴϷϹϺϽϾϿἀἁἂἃἄἅἆἇἈἉἊἋἌἍἎἏἐἑἒἓἔἕἘἙἚἛἜἝἠἡἢἣἤἥἦἧἨἩἪἫἬἭἮἯἰἱἲἳἴἵἶἷἸἹἺἻἼἽἾἿὀὁὂὃὄὅὈὉὊὋὌὍὐὑὒὓὔὕὖὗὙὛὝὟὠὡὢὣὤὥὦὧὨὩὪὫὬὭὮὯὰάὲέὴήὶίὸόὺύὼώᾀᾁᾂᾃᾄᾅᾆᾇᾈᾉᾊᾋᾌᾍᾎᾏᾐᾑᾒᾓᾔᾕᾖᾗᾘᾙᾚᾛᾜᾝᾞᾟᾠᾡᾢᾣᾤᾥᾦᾧᾨᾩᾪᾫᾬᾭᾮᾯᾰᾱᾲᾳᾴᾶᾷᾸᾹᾺΆᾼι῁ῂῃῄῆῇῈΈῊΉῌ῍῎῏ῐῑῒΐῖῗῘῙῚΊ῝῞῟ῠῡῢΰῤῥῦῧῨῩῪΎῬ῭΅`ῲῳῴῶῷῸΌῺΏῼ´"
    private const val TO = "aaaaaaæceeeeiiiiðnoooooøuuuuyþaaaaaaceeeeiiiinooooouuuuyyaaaaaaccccccccddđeeeeeeeeeegggggggghhħiiiiiiiiiĳjjkkllllllŀłnnnnnnŋooooooœrrrrrrssssssssttttŧuuuuuuuuuuuuwwyyyzzzzzzɓƃƅɔƈɖɗƌǝəɛƒɠɣɩɨƙɯɲɵooƣƥʀƨʃƭʈuuʊʋƴƶʒƹƽǆǆǉǉǌǌaaiioouuuuuuuuuuaaaaææǥggkkooooʒʒjǳǳggƕƿnnaaææøøaaaaeeeeiiiioooorrrruuuussttȝhhƞȣȥaaeeooooooooyyⱥȼƚⱦɂƀʉʌɇɉɋɍɏaabbbbbbccddddddddddeeeeeeeeeeffgghhhhhhhhhhiiiikkkkkkllllllllmmmmmmnnnnnnnnoooooooopppprrrrrrrrssssssssssttttttttuuuuuuuuuuvvvvwwwwwwwwwwxxxxyyzzzzzzhtwyſßaaaaaaaaaaaaaaaaaaaaaaaaeeeeeeeeeeeeeeeeiiiioooooooooooooooooooooooouuuuuuuuuuuuuuyyyyyyyyỻỽỿͱͳʹͷ;ϳ¨α·εηιουωιαβγδεζηθικλμνξοπρστυφχψωιυαεηιυιυουωϗϒϒϙϛϝϟϡϣϥϧϩϫϭϯθϸϲϻͻͼͽααααααααααααααααεεεεεεεεεεεεηηηηηηηηηηηηηηηηιιιιιιιιιιιιιιιιοοοοοοοοοοοουυυυυυυυυυυυωωωωωωωωωωωωωωωωααεεηηιιοουυωωααααααααααααααααηηηηηηηηηηηηηηηηωωωωωωωωωωωωωωωωααααααααααααι¨ηηηηηεεηηη᾿᾿᾿ιιιιιιιιιι῾῾῾υυυυρρυυυυυυρ¨¨`ωωωωωοοωωω´"
    private val map: Map<Char, Char> = HashMap<Char, Char>(FROM.length * 2).apply {
        for (i in FROM.indices) put(FROM[i], TO[i])
    }

    fun fold(input: String): String {
        val out = StringBuilder(input.length)
        for (c in input) {
            val code = c.code
            when {
                // Hebrew points, accents (cantillation) and other marks; maqaf and sof pasuq become spaces.
                code in 0x0591..0x05C7 -> if (c == '\u05BE' || c == '\u05C3' || c == '\u05C0') out.append(' ')
                // Combining accents left over from decomposed input, and transliteration signs.
                code in 0x0300..0x036F -> Unit
                c == 'ʼ' || c == 'ʽ' || c == 'ʾ' || c == 'ʿ' || c == '\'' || c == '’' || c == '‘' || c == '.' || c == '·' -> Unit
                c == '-' || c == '‐' || c == '_' -> Unit
                else -> {
                    val m = map[c] ?: c
                    val lower = m.lowercaseChar()
                    out.append(if (lower == 'ς') 'σ' else lower)
                }
            }
        }
        return out.toString()
    }

    /** True when [s] contains Hebrew letters. */
    fun isHebrew(s: String): Boolean = s.any { it.code in 0x05D0..0x05EA }

    /** True when [s] contains Greek letters. */
    fun isGreek(s: String): Boolean = s.any { it.code in 0x0370..0x03FF || it.code in 0x1F00..0x1FFF }
}
