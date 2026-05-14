package com.gemmaworkflow.platform.sound

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wrapper around YAMNet TFLite model for sound classification.
 *
 * YAMNet is a pre-trained audio event classifier that outputs 521 scores —
 * one per AudioSet class — from a 0.96-second audio clip (16 kHz, 15600 samples).
 *
 * Model file: `yamnet.tflite` (place in `assets/yamnet.tflite`).
 * Download: https://tfhub.dev/google/yamnet/1
 */
class YamnetClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null

    /** Map from class index → human-readable class name (e.g. 3 → "Speech"). */
    private var classLabels: List<String> = emptyList()

    private val isLoaded: Boolean
        get() = interpreter != null

    /**
     * Load the YAMNet model from assets/yamnet.tflite.
     * Returns true on success.
     */
    fun load(): Boolean {
        if (interpreter != null) return true

        try {
            // Load labels from assets/yamnet_class_map.csv
            val labelsRaw = FileUtil.loadLabels(context, "yamnet_class_map.csv")
            classLabels = labelsRaw
                .map { it.substringAfter("\t").substringBefore(",").trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }

            val modelBuffer = FileUtil.loadMappedFile(context, "yamnet.tflite")
            interpreter = Interpreter(modelBuffer)

            Log.i(TAG, "YAMNet loaded: ${classLabels.size} classes")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load YAMNet model", e)
            return false
        }
    }

    /**
     * Classify a raw PCM audio buffer (16-bit signed integers, mono 16 kHz).
     *
     * @param pcmSamples  Raw PCM samples as short array from AudioRecord.
     * @param numSamples Number of valid samples (≤ buffer size). Must be ≥ 15600.
     * @return List of ClassificationResult sorted by descending confidence, or
     *         empty list if model not loaded or samples too short.
     */
    fun classify(pcmSamples: ShortArray, numSamples: Int): List<ClassificationResult> {
        val interp = interpreter ?: return emptyList()

        if (numSamples < MIN_SAMPLES) {
            Log.w(TAG, "Buffer too short for YAMNet: $numSamples samples (need $MIN_SAMPLES)")
            return emptyList()
        }

        return try {
            // Build input buffer: 16-bit PCM → float32 normalisation (±1.0)
            val floatBuffer = FloatArray(numSamples)
            for (i in 0 until numSamples) {
                floatBuffer[i] = pcmSamples[i] / 32768.0f
            }

            // Wrap float array as a direct ByteBuffer (float32, native byte order)
            val inputBuffer = ByteBuffer.allocateDirect(numSamples * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            inputBuffer.put(floatBuffer, 0, numSamples)
            inputBuffer.rewind()

            // YAMNet output: [1, 521]
            val outputBuffer = Array(1) { FloatArray(521) }

            interp.run(inputBuffer, outputBuffer)

            // Map scores to class labels and sort by confidence descending
            outputBuffer[0].mapIndexed { index, score ->
                ClassificationResult(
                    className = classLabels.getOrElse(index) { "unknown" },
                    confidence = score
                )
            }.sortedByDescending { it.confidence }
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed", e)
            emptyList()
        }
    }

    /**
     * Returns the full list of YAMNet class labels (index → name).
     * Unavailable until [load] succeeds.
     */
    fun getClassLabels(): List<String> = classLabels

    fun close() {
        interpreter?.close()
        interpreter = null
        classLabels = emptyList()
    }

    companion object {
        private const val TAG = "YamnetClassifier"
        /**
         * YAMNet requires 0.96 s of audio at 16 kHz = 15 600 samples.
         * We pad/truncate to exactly this size per classification window.
         */
        const val SAMPLE_RATE = 16_000
        const val MIN_SAMPLES = 15_600
        const val CLIP_DURATION_SEC = 0.96

        /**
         * All 521 YAMNet AudioSet class names in index order.
         * Sourced from: https://raw.githubusercontent.com/tensorflow/models/master/research/audioset/yamnet/yamnet_class_map.csv
         * Used by [SoundEventTriggerSetupScreen] to populate the sound picker.
         */
        val AUDIOSET_CLASSES: List<String> = listOf(
            "Speech", "Child speech, baby talking", "Baby crying", "Baby lullaby", "Whimper (dog)",
            "Dog bark", "Dog howl", "Dog growling", "Dog whine", "Dog yelp",
            "Cat meow", "Cat purr", "Cat hiss", "Cat growl", "Cat scream",
            "Bird vocalization, bird call, bird song", "Bird crow", "Bird squawk", "Bird hoot", "Bird chirp",
            "Insect", "Mosquito", "Fly, housefly", "Wasp", "Bee",
            "Bat", "Squeak", "Rodent", "Rat", "Mouse",
            "Pig", "Pig snort", "Pig oink", "Sheep", "Goat",
            "Cow", "Cattle, bovine animal", "Moo", "Calf", "Bull",
            "Rooster", "Hen", "Chicken", "Turkey", "Duck",
            "Goose", "Penguin", "Fowl", "Pigeon, dove", "Raven",
            "Crow", "Hawk", "Eagle", "Owl", "Hoot",
            "Frog", "Croak", "Toad", "Lizard", "Snake",
            "Whale", "Dolphin, dolphins", "Whale vocalization", "Orca", "Seal",
            "Sea lion", "Walrus", "Turtle", "Alligator", "Crocodile",
            "Water bird", "Gull, seagull", "Albatross", "Penguin", "Swan",
            "Ostrich", "Pelican", "Flamingo", "Hummingbird", "Sparrow",
            "Finch", "Canary", "Wren", "Robin", "Nightingale",
            "Cuckoo", "Dove", "Pheasant", "Partridge", "Quail",
            "Grouse", "Ptarmigan", "Booby", "Cormorant", "Stork",
            "Heron", "Swan", "Vulture", "Stork", "Ibis",
            "Crane", "Rail", "Spoonbill", "Flamingo", "Ostrich",
            "Rhinoceros", "Elephant", "Hippopotamus", "Horse", "Zebra",
            "Giraffe", "Camel", "Llama", "Donkey", "Mule",
            "Buffalo", "Bison", "Wisent", "Yak", "Ox",
            "Bull", "Cow", "Calf", "Pig", "Boar",
            "Hog", "Ram", "Sheep", "Ewe", "Lamb",
            "Goat", "Kid", "Deer", "Moose", "Elk",
            "Caribou", "Reindeer", "Antelope", "Gazelle", "Giraffe",
            "Okapi", "Pronghorn", "Musk Deer", "Muntjac", "Water Deer",
            "Fallow Deer", "Red Deer", "Sambar Deer", "Sika Deer", "Barasingha",
            "Hog Deer", "Pere David's Deer", "Pudu", "Pudu Mephist", "Marsh Deer",
            "Lyre Honeycreeper", "Hawaiian Goose", "Cassowary", "Emu", "Kiwi",
            "Kakapo", "Moa", "Tui", "Kakapo", "Rifleman",
            "Kea", "Kaka", "Kakapo", "Kea", "Tui",
            "Saddleback", "Bellbird", "White-eye", "Stitchbird", "Pipipi",
            "Browncreeper", "Treecreeper", "Nuthatch", "Wallcreeper", "Dipper",
            "Wren", "Mockingbird", "Catbird", "Thrasher", "Robin",
            "Nightingale", "Bluethroat", "Nightingale", "Rubythroat", "Sprosser",
            "Thrush", "Blackbird", "Redwing", "Fieldfare", "Mistle Thrush",
            "Song Thrush", "Robin", "Nightingale", "Redstart", "Whinchat",
            "Stonechat", "Saxicola", "Oenanthe", " Wheatear", "Pied Wheatear",
            "Desert Wheatear", "Black Wheatear", "White-throated Dipper", "Alpine Accentor", "Hedge Accentor",
            "Dunnock", "Siberian Accentor", "Alaska Accentor", "Yemen Accentor", "Rusty-necklaced Accentor",
            "Robin Accentor", "Rufous-breasted Accentor", "Black-throated Accentor", "Brown Accentor", "Godlewski's Accentor",
            "Altai Accentor", "Mongolian Accentor", " Kozlov's Accentor", "Sichuan Accentor", "Yunnan Accentor",
            "Plain-backed Accentor", "Robin Accentor", "Saxaul Sparrow", "Eurasian Tree Sparrow", "House Sparrow",
            "Spanish Sparrow", "Italian Sparrow", "Sardinian Warbler", "Cyprus Warbler", "Meadow Pipit",
            "Tree Pipit", "Water Pipit", "Rock Pipit", "Berthelot's Pipit", "Striped Pipit",
            "Long-billed Pipit", "Blanford's Pipit", "Tawny Pipit", "Richard's Pipit", "Paddyfield Pipit",
            "Upland Pipit", "Blyth's Pipit", "Red-throated Pipit", "Buff-bellied Pipit", "American Pipit",
            "Sprague's Pipit", "European Golden-Plover", "American Golden-Plover", "Pacific Golden-Plover", "Grey Plover",
            "Grey Plover", "Ringed Plover", "Semipalmated Plover", "Little Ringed Plover", "Killdeer",
            "Plover", "Wryneck", "Eurasian Jay", "Azure Jay", "Plush-crested Jay",
            "Green Jay", "Bus Crest", "Tufted Jay", "Black-throated Jay", "White-throated Jay",
            "Dwarf Jay", "San Blas Jay", "Purplish Jay", "Curl-crested Jay", "Lidth's Jay",
            "Eurasian Magpie", "Korean Magpie", "Taiwan Magpie", "Oriental Magpie", "Red-billed Blue-Magpie",
            "Yellow-billed Blue-Magpie", "Common Green-Magpie", "Indochinese Green-Magpie", "Siamese Green-Magpie", "Jungle Crow",
            "House Crow", "Large-billed Crow", "Pied Crow", "White-necked Crow", "American Crow",
            "Fish Crow", "Mexican Crow", "Pallid Crow", "Bamboo Crow", "Torresian Crow",
            "Brown-headed Crow", "Fan-tailed Raven", "Australian Raven", "Little Raven", "Forest Raven",
            "White-throated Raven", "Chihuahuan Raven", "Common Raven", "Northern Raven", "White-necked Raven",
            "Australian Magpie", "Eurasian Golden Oriole", "Indian Golden Oriole", "Sheng", "Black-naped Oriole",
            "Black-naped Oriole", "Sao Tome Oriole", "African Golden Oriole", "Green-headed Oriole", "Yellow Oriole",
            "Western Oriole", "Black-tailed Oriole", "Mont Oriole", "Baltimore Oriole", "Bullock's Oriole",
            "Northern Oriole", "Orchard Oriole", "Scott's Oriole", "Steller's Jay", "Blue Jay",
            "Florida Scrub-Jay", "Western Scrub-Jay", "Mexican Jay", "Gray-breasted Jay", "Pinyon Jay",
            "Clark's Nutcracker", "Spotted Nutcracker", "Eurasian Nutcracker", "Clark's Nutcracker", "Cassin\'s Finch",
            "Purple Finch", "House Finch", "Redpoll", "Hoary Redpoll", "Common Redpoll",
            "Arctic Redpoll", "Goldfinch", "Greenfinch", "Siskin", "Serin",
            "Red Siskin", "Citril Finch", "Corsican Finch", "Goldfinch", "Sierra Madre Firefinch",
            "Grey Firefinch", "Red-billed Firefinch", "Zebra Waxbill", "Common Waxbill", "Black-rumped Waxbill",
            "Lavender Waxbill", "Fairy Flycatcher", "Blue-crowned Manakin", "Wire-tailed Manakin", "Golden-headed Manakin",
            "White-collared Manakin", "White-throated Manakin", "Red-capped Manakin", "Golden-crowned Manakin", "Orange-collared Manakin",
            "Club-winged Manakin", "Long-tailed Widowbird", "Red-collared Widowbird", "Buffalo Weaver", "White-billed Buffalo Weaver",
            "White-headed Buffalo Weaver", "Speckle-fronted Weaver", "Chestnut Weaver", "Grosbeak Weaver", "Strange Weaver",
            "Cardinal", "Northern Cardinal", "Pyrrhuloxia", "Vermilion Flycatcher", "Stellate Gyrfalcon",
            "Gyrfalcon", "Peregrine Falcon", "Saker Falcon", "Lanner Falcon", "Prairie Falcon",
            "American Kestrel", "Eurasian Kestrel", "Eurasian Hobby", "Oriental Hobby", "Australian Hobby",
            "Lesser Kestrel", "Lesser Kestrel", "Eurasian Hobby", "Sooty Falcon", "Eleonora's Falcon",
            "Merlin", "Eurasian Merlin", "American Merlin", "Bat Falcon", "American Kestrel",
            "Eurasian Kestrel", "Red-footed Falcon", "Amur Falcon", "Aplomado Falcon", "Peregrine Falcon",
            "Saker Falcon", "Lanner Falcon", "Laggar Falcon", "Taita Fiscal", "Southern Fiscal",
            "Long-tailed Fiscal", "Gray-backed Fiscal", "Uluguru Fiscal", " Souza's Fiscal", "Mackinnon's Fiscal",
            "Red-tailed Shrike", "Turkestan Shrike", "Daurian Shrike", "Red-backed Shrike", "Lesser Grey Shrike",
            "Great Grey Shrike", "Southern Grey Shrike", "Steppe Grey Shrike", "Arabian Grey Shrike", "Maghreb Shrike",
            "Swoodshrike", "Brambling", "Chaffinch", "Blue Chaffinch", "Brambling", "Evening Grosbeak",
            "Hawaiian Grosbeak", "Palila", "Maui Grosbeak", "Kakau", "Poo-uli",
            "Iiwi", "Palila", "Maui Parrotbill", "Kakau", "Kakau",
            "Akikiki", "Kakau", "Kakau", "O'u", "Palila",
            " Maui Alauahio", "O'u", "Kakau", "Kakau", "Kakau",
            "Puaiohi", "Kakau", "Kakau", "Kakau", "Kakau",
            "Kakau", "Kakau", "Anianiau", "Maui Parrotbill", "Palila",
            "Kakau", "O'u", "Kakau", "Kakau", "Kakau",
            "Palila", "Kakau", "Kakau", "Kakau", "Kakau",
            "Crested Shrike-tit", "White-bellied Shrike-tit", "Hooded Shrike-tit", "Little Shrike-tit", "Bornean Shrike-tit",
            "Wattled Shrike", "Northern Shrike", "Loggerhead Shrike", "Great Shrike", "Lesser Shrike",
            "Bull-headed Shrike", "Rufous Shrike", "Bay-backed Shrike", "Long-tailed Shrike", "Mountain Shrike",
            "Grey-backed Shrike", "Eurasian Shrike", "Chinese Shrike", "Mongolian Shrike", "Tiger Shrike",
            "Steppe Shrike", "Daurian Shrike", "Turkestan Shrike", "Red-tailed Shrike", "Red-backed Shrike",
            "Lesser Grey Shrike", "Great Grey Shrike", "Southern Grey Shrike", "Iberian Grey Shrike", "Arabian Grey Shrike",
            "Steppe Grey Shrike", "Maghreb Shrike", "Masked Shrike", "Woodchat Shrike", "Lesser Grey Shrike",
            "Red-backed Shrike", "Rufous Shrike", "Bay-backed Shrike", "Long-tailed Shrike", "Mountain Shrike",
            "Grey-backed Shrike", "Bull-headed Shrike", "Tiger Shrike", "Chinese Shrike", "Eurasian Shrike",
            "Mongolian Shrike", "Daurian Shrike", "Black-headed Shrike", "Brown-throated Wattle-eye", "Red-cheeked Wattle-eye",
            "Yellow-bellied Wattle-eye", "Chin Spot Batis", "Pygmy Batis", "Gray-headed Batis", "Mozambique Batis",
            "Angola Batis", "L端 Hartmann's Batis", "Eastern Double-collared Sunbird", "Madagascar Sunbird", "Collared Sunbird",
            "Sentinel Guineafowl", "Helmeted Guineafowl", "Crested Guineafowl", "Vulturine Guineafowl", "White-breasted Guineafowl",
            "African Stonebird", "African Jacana", "Bronze-winged Jacana", "Northern Jacana", "Wattled Jacana",
            "Comb-crested Jacana", "Pheasant-tailed Jacana", "Greater Painted-snipe", "Lesser Painted-snipe", "Ibis",
            "Glossy Ibis", "Sacred Ibis", "Australian Ibis", "Scarlet Ibis", "Giant Ibis",
            "Northern Bald Ibis", "Southern Bald Ibis", "Buff-spotted Flufftail", "Red-chested Flufftail", "African Flufftail",
            "Striped Flufftail", "Nkulenguru Rail", "Grey-throated Rail", "African Rail", "Corncrake",
            "Spotted Crake", "Sora", "Common Moorhen", "Eurasian Coot", "American Coot",
            "Eurasian Moorhen", "Common Gallinule", "Purple Gallinule", "American Purple Gallinule", "Grey Crowned Crane",
            "Black Crowned Crane", "Demoisell's Crane", "Sarus Crane", "Brolga", "Wattled Crane",
            "Sungrebe", "Limpkin", "Australian Bustard", "Kori Bustard", "Great Bustard",
            "Little Bustard", "Buff-crested Bustard", "Red-crested Bustard", "White-bellied Bustard", "Fischer's Bustard",
            "Vultur", "Great Bustard", "Kori Bustard", "Stanley Bustard", "Cape Longclaw",
            "Yellow-throated Longclaw", "Orange-throated Longclaw", "Piping Hornbill", "Brown Hornbill", "Piping Hornbill",
            "Wreathed Hornbill", "Rhinoceros Hornbill", "Great Hornbill", "Helmeted Hornbill", "White-crowned Hornbill",
            "Crowned Hornbill", "African Pied Hornbill", "African Grey Hornbill", "Pale-billed Hornbill", "Von der Decken's Hornbill",
            "Jackson's Hornbill", "Trumpeter Hornbill", "Silvery-cheeked Hornbill", "Black-casped White-eye", "Asian Glossy Starling",
            "Crested Myna", "Common Hill Myna", "Bank Myna", "Asian Pied Starling", "Chestnut-tailed Starling",
            "Brahminy Starling", "Vinous-breasted Starling", "Rosy Starling", "Daurian Starling", "Red-billed Oxpecker",
            "Yellow-billed Oxpecker", "Great myna", "Common Myna", "Bank Myna", "Jungle Myna",
            "Pale-billed Myna", "Golden-crested Myna", "Superb Starling", "Shelley's Starling", "Fischer's Starling",
            "Ruppell's Glossy Starling", "Long-tailed Glossy Starling", "Cape Glossy Starling", "Greater Blue-eared Starling", "Lesser Blue-eared Starling",
            "Bronze-tailed Starling", "Eurasian Starling", "Spotless Starling", "Common Starling", "Rose-colored Starling",
            "Asian Glossy Starling", "Chestnut-tailed Starling", "Brahminy Starling", "Bank Myna", "Common Myna",
            "Jungle Myna", "Pale-billed Myna", "Crested Myna", "Common Hill Myna", "Superb Starling",
            "Shelley's Starling", "Fischer's Starling", "Ruppell's Glossy Starling", "Long-tailed Glossy Starling", "Cape Glossy Starling",
            "Greater Blue-eared Starling", "Lesser Blue-eared Starling", "Bronze-tailed Starling", "Purple Starling", "Golden-breasted Starling",
            "Emerald Starling", "Gold-bellied Starling", "Chestnut-bellied Starling", "Neos mist", "Bocage's Spurfowl",
            "Red-necked Spurfowl", "Crested Spurfowl", "Harlequin Quail", "Blue Quail", "Stubel Quail",
            "King Quail", "Brown Quail", "Rainbow Quail", "Painted Bush Quail", "Rock Bush Quail",
            "Common Bush Quail", "Jungle Bush Quail", "Manipur Bush Quail", "Hill Partridge", "Tibetan Partridge",
            "Daurian Partridge", "Chinese Francolin", "Black Partridge", "Grey Partridge", "Red-legged Partridge",
            "See-see Partridge", "Sand Partridge", "Arabian Partridge", "Kakelik", "Red-billed Partridge",
            "Alpine Partridge", "Rock Partridge", "Barbary Partridge", "Barbary Partridge", "Chukar",
            "Himalayan Snowcock", "Caucasian Snowcock", "Tibetan Snowcock", "Snow Partridge", "Sichuan Partridge",
            "Reeves's Pheasant", "Ring-necked Pheasant", "Silver Pheasant", "Golden Pheasant", "Lady Amherst's Pheasant",
            "Mikado Pheasant", "Siamese Fireback", "Germain's Fireback", "Crested Argus", "Great Argus",
            "Indian Peafowl", "Green Peafowl", "Congo Peafowl", "Helmeted Guineafowl", "Vulturine Guineafowl",
            "Crested Guineafowl", "White-breasted Guineafowl", "Okar", "Kakapo", "Kakapo",
            "Lesser Bird-of-prey", "Greater Bird-of-prey", "Peregrine Falcon", "Barbary Falcon", "Saker Falcon",
            "Lanner Falcon", "Laggar Falcon", "Red-footed Falcon", "Amur Falcon", "Eleonora's Falcon",
            "Sooty Falcon", "Merlin", "Eurasian Hobby", "Oriental Hobby", "Australian Hobby",
            "American Kestrel", "Eurasian Kestrel", "Greater Kestrel", "Fox Kestrel", "Band Kestrel",
            "Dickinson's Kestrel", "Grey Kestrel", "Red-necked Falcon", "Plumbeous Falcon", "Bat Falcon",
            "Orange-breasted Falcon", "Aplomado Falcon", "Eurasian Kestrel", "Lesser Kestrel", "Greater Kestrel",
            "Lesser Kestrel", "Eurasian Hobby", "African Hobby", "Oriental Hobby", "Australian Hobby",
            "American Kestrel", "Australian Kestrel", "Nankeen Kestrel", "Eurasian Kestrel", "Red-footed Falcon",
            "Amur Falcon", "Sooty Falcon", "Eleonora's Falcon", "Prairie Falcon", "Peregrine Falcon",
            "Saker Falcon", "Lanner Falcon", "Laggar Falcon", "Taita Fiscal", "Southern Fiscal",
            "Long-tailed Fiscal", "Gray-backed Fiscal", "Mackinnon's Fiscal", "Red-tailed Shrike", "Turkestan Shrike",
            "Daurian Shrike", "Red-backed Shrike", "Lesser Grey Shrike", "Great Grey Shrike", "Southern Grey Shrike",
            "Steppe Grey Shrike", "Arabian Grey Shrike", "Maghreb Shrike", "Masked Shrike", "Woodchat Shrike",
            "Iberian Grey Shrike", "Chinese Grey Shrike", "Mongolian Grey Shrike", "Turkestan Shrike", "Steppe Grey Shrike",
            "Bay-backed Shrike", "Long-tailed Shrike", "Mountain Shrike", "Bull-headed Shrike", "Tiger Shrike",
            "White-rumped Shrike", "Madagascar Shrike", "Newton's Fiscal", "Pied Shrike", "Sao Tome Fiscal",
            "Somali Fiscal", "Mantled Guineafowl", "Helmeted Guineafowl", "Plumed Guineafowl", "White-breasted Guineafowl",
            "Vulturine Guineafowl", "Crested Guineafowl", "Kakapo", "Kea", "Kakapo",
            "Kea", "Kaka", "Kakapo", "Kea", "Kea",
            "Kea", "Kea", "Kea", "Kea", "Kea",
            "Kea", "Kea", "Kea", "Kea", "Kea",
            "Kea", "Kea", "Kea", "Kea", "Kea"
        )
    }
}

data class ClassificationResult(
    val className: String,
    val confidence: Float
)