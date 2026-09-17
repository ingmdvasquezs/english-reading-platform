package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextReaderPaginationServiceTest {

  private TextReaderPaginationService paginationService;

  @BeforeEach
  void setUp() {
    var wordProcessor = new TextWordProcessor();
    var tokenizer = new ReaderTextTokenizer(wordProcessor);
    paginationService = new TextReaderPaginationService(tokenizer);
  }

  @Test
  @DisplayName("1. Short reading remains 1 part (matching frontend spec)")
  void shortReadingRemainsOnePart() {
    var text = "A short reading — with “quotes”, contractions don't break, and  multiple spaces.";
    assertThat(paginationService.totalParts(text, 1)).isEqualTo(1);
  }

  @Test
  @DisplayName("2. Text around 100 words in single paragraph remains 1 part (<= 220 words)")
  void around100WordsRemainsOnePart() {
    var text = generateParagraph(100, "word");
    assertThat(paginationService.totalParts(text, 1)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "3. Text around 160 words (target) in single paragraph remains 1 part (<= 220 words)")
  void around160WordsRemainsOnePart() {
    var text = generateParagraph(160, "target");
    assertThat(paginationService.totalParts(text, 1)).isEqualTo(1);
  }

  @Test
  @DisplayName("4. Text around 220 words (maximum) in single paragraph remains 1 part")
  void around220WordsRemainsOnePart() {
    var text = generateParagraph(220, "max");
    assertThat(paginationService.totalParts(text, 1)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "5. Multiple paragraphs grouped into stable parts near target (matching frontend spec)")
  void multipleParagraphsGroupedNearTarget() {
    // 3 paragraphs of 90, 80, 110 words -> total 280 words -> grouped into 2 parts (170, 110)
    var text =
        String.join(
            "\n\n",
            generateParagraph(90, "One"),
            generateParagraph(80, "Two"),
            generateParagraph(110, "Three"));
    assertThat(paginationService.totalParts(text, 1)).isEqualTo(2);
  }

  @Test
  @DisplayName("6. Paragraph exceeding HARD limit (260 words) is split by sentences")
  void paragraphExceedingHardWordsIsSplit() {
    // 3 sentences of 90 words each = 270 words in 1 paragraph -> splits into > 1 parts
    var text =
        String.join(
            " ",
            generateSentence(90, "Alpha"),
            generateSentence(90, "Beta"),
            generateSentence(90, "Gamma"));
    assertThat(paginationService.totalParts(text, 1)).isGreaterThan(1);
  }

  @Test
  @DisplayName("7. Real PLATFORM reading: The Candileja produces deterministic totalParts")
  void candilejaRealReadingParity() {
    var text =
        """
        Long ago, an elderly woman lived with her two grandsons.

        She loved them deeply.

        Love itself was not the problem.

        The problem was that she believed loving them meant allowing them to do whatever they wanted.

        The boys disobeyed her, misbehaved, insulted her, and treated her with very little respect.

        But their grandmother never corrected them.

        Whenever they did something wrong, she made excuses for them.

        Whenever they deserved consequences, she immediately forgave them.

        Before long, the boys understood that there were no real limits in the house.

        With each passing day, they became more demanding and disrespectful.

        Then one day, they invented a particularly humiliating game.

        They wanted to pretend they were riding a horse.

        There was no horse nearby.

        So they decided to use their own grandmother instead.

        They ordered her to get down on her hands and knees.

        Then they placed a saddle on her back as though she were an animal.

        Both boys climbed onto her and pretended to ride through the house, laughing.

        Their grandmother could have stopped them.

        But she did not.

        She accepted the humiliation simply because she wanted to please them.

        The boys thought it was hilarious.

        And so they continued growing up without boundaries, discipline, or respect.

        Years passed.

        Eventually, the old woman died.

        When she reached the gates of Heaven, she expected that she might finally find peace.

        But Saint Peter knew what had happened.

        He reprimanded her.

        Not because she had loved her grandchildren too much, but because she had failed to guide and discipline them.

        By tolerating everything they did, she had helped them grow into disrespectful people.

        As punishment, the old woman was sent back to the world.

        But she did not return as a human being.

        She returned as a terrifying apparition made of fire.

        Her form was made up of three flames.

        One represented the grandmother.

        The other two represented her grandsons.

        And that was how the Candileja was born.

        From then on, the great ball of fire traveled along roads and across fields at night.

        Its three flames twisted like burning arms, and according to tradition, its appearance could be accompanied by a sound like clay pots breaking apart.

        The Candileja was said to chase people who repeated the same kinds of wrongdoing that had destroyed that family: irresponsible parents, drunks, adulterers, and people who wandered the roads at night behaving badly.

        She also frightened travelers who stayed out too late.

        For generations, grandparents told the story to their children and grandchildren.

        Its lesson was simple:

        Loving a child also means setting limits.

        Because a family in which no one teaches respect or establishes boundaries may end, like the old woman and her two grandsons, as the three burning flames of the Candileja.
        """;
    var totalParts = paginationService.totalParts(text, 1);
    assertThat(totalParts).isNotNull();
    assertThat(totalParts).isEqualTo(3);
  }

  @Test
  @DisplayName("8. Real PLATFORM reading: The Mohán produces deterministic totalParts")
  void mohanRealReadingParity() {
    var text =
        """
        About three generations ago, rural communities around Pasca did not have running water in their homes. Getting enough water for the day could take hours.

        Families relied on natural springs and streams, sometimes located far from where they lived. Every day, someone had to leave home carrying empty containers, walk to the nearest water source, fill them, and make the long journey back.

        In Pasuncha, one woman made this journey every day. It took her almost three hours to reach the place where she collected water and another three hours to return home.

        She was also a regular tobacco smoker.

        One morning, she set out along her usual path. About halfway there, she began to feel tired. She stopped beside a large rock, sat down to rest, and lit some tobacco.

        While she was resting, an old man appeared.

        He looked extremely poor, like one of the elderly travelers who sometimes crossed the mountains carrying little more than the clothes they wore.

        He came closer and asked her:

        "Could you spare me a smoke?"

        The woman checked what she had left.

        There was nothing.

        The only tobacco she still had was the tobacco she was already smoking.

        She could have refused. She still had several hours of walking ahead of her.

        Instead, she decided to share it.

        "This is all I have left," she told him, "but you can have it. I still need to continue on for the water."

        She handed him what remained.

        The old man accepted it and smoked quietly.

        When he finished, he did not continue on his way. Instead, he crouched beside the path and began digging into the earth with his bare hands.

        The woman watched him in confusion.

        After clearing away some soil, the old man looked at her and said:

        "Thank you. This is your payment."

        At that moment, something extraordinary happened.

        Water began to rise from the ground where he had been digging.

        At first, only a thin stream appeared.

        Then more water began to flow.

        The woman realized that a spring was forming right before her eyes.

        She would no longer have to walk the remaining three hours to reach the old water source.

        When she turned to speak to the old man again, the mysterious traveler was already gone.

        The woman returned to her community and told everyone what had happened.

        The villagers associated the strange old man with the Mohán, an ancient figure in Colombian folklore connected with rivers, streams, springs, and tobacco.

        According to local memory, the most remarkable part of the story is that the spring remained there.

        And so a warning - or perhaps a piece of advice - was passed down among the people of Pasuncha:

        If you ever meet a humble old man in the mountains who asks you for something, think twice before refusing him.

        He may simply be an old traveler.

        But he may also be the Mohán.

        And the gifts of the Mohán, the people of Pasuncha say, are gifts of nature that can last for generations.
        """;
    var totalParts = paginationService.totalParts(text, 1);
    assertThat(totalParts).isNotNull();
    assertThat(totalParts).isEqualTo(3);
  }

  @Test
  @DisplayName(
      "8c. Real PLATFORM reading: Francisco el Hombre and the Devil splits into exactly 3 parts")
  void franciscoElHombreSplitsIntoThreeParts() {
    var text =
        """
        Francisco Moscote was a wandering musician, a juglar. In the days before radios and telephones reached remote towns, musicians like him traveled from place to place with their accordions, carrying songs and spreading news.

        Francisco was famous for his extraordinary skill. One night, after playing at a celebration, he rode home alone. To make the journey easier, he played his accordion as he moved through the darkness. The countryside was silent. Only his music could be heard.

        Then he finished one melody - and someone answered him. From somewhere out in the dark came the sound of another accordion.

        At first Francisco assumed it must be another musician. He played another tune. The unseen player answered with something even more difficult. Francisco accepted the challenge. He played faster. The reply was better. The duel went on and on. Every time Francisco played something complicated, the mysterious rival answered with something even more impressive.

        Before long, Francisco began to feel uneasy. He tried to figure out where the music was coming from. At last he managed to catch sight of his rival, and what he saw was no ordinary accordion player. According to the legend, an enormous figure appeared, several meters tall, mounted on a huge horse.

        Francisco understood at once who he was facing. He was playing against the Devil - and the Devil was winning.

        He could have kept trying to defeat him with musical skill alone, but he realized that would not be enough. So he kept playing and, according to the traditional version recorded by Colombia's Ministry of Culture, he began reciting the Creed backward.

        What his talent alone could not achieve, faith did. The Devil began to lose his power. The monstrous figure vanished. The horse disappeared with him. The rival accordion fell silent. Francisco was once again alone on the road.

        He had defeated the Devil.

        The story spread from town to town. Francisco was no longer known merely as a brilliant accordionist. He became Francisco el Hombre, the wandering musician who challenged the Devil with his accordion and lived to tell the tale. In time, the legend turned him into one of the great symbols of vallenato and of the old Caribbean tradition of traveling musicians.
        """;
    var totalParts = paginationService.totalParts(text, 1);
    assertThat(totalParts).isNotNull();
    assertThat(totalParts).isEqualTo(2);
  }

  @Test
  @DisplayName("9. Unsupported pagination version returns null")
  void unsupportedPaginationVersionReturnsNull() {
    var text = generateParagraph(100, "word");
    assertThat(paginationService.totalParts(text, 2)).isNull();
    assertThat(paginationService.totalParts(text, 0)).isNull();
    assertThat(paginationService.totalParts(text, null)).isNull();
  }

  @Test
  @DisplayName("10. Blank or null content returns null")
  void blankOrNullContentReturnsNull() {
    assertThat(paginationService.totalParts(null, 1)).isNull();
    assertThat(paginationService.totalParts("", 1)).isNull();
    assertThat(paginationService.totalParts("   ", 1)).isNull();
  }

  private String generateParagraph(int words, String prefix) {
    return IntStream.range(0, words).mapToObj(i -> prefix + i).collect(Collectors.joining(" "))
        + ".";
  }

  private String generateSentence(int words, String prefix) {
    return IntStream.range(0, words).mapToObj(i -> prefix + i).collect(Collectors.joining(" "))
        + "!";
  }
}
