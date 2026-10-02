package com.nazjara;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Real-world review texts shared by the examples.
 */
final class Reviews {

    static final List<String> TUMBLER = List.of(
            """
            I recently purchased the Stanley 40oz Tumbler in the vibrant Citron color, and I am thoroughly impressed with its performance in every aspect. From its sleek design to its remarkable durability and easy washability, this tumbler has quickly become my go-to companion for all my hydration needs.

            First and foremost, the Citron color is absolutely stunning. It's bright, cheerful, and adds a pop of personality to my everyday routine. Whether I'm sipping on my flavored water or staying hydrated during a busy work shift, this tumbler stands out in the best way possible.

            In terms of durability, the Stanley 40oz Tumbler exceeds expectations. Constructed from high-quality stainless steel, it's built to withstand the rigors of daily use and outdoor adventures. I've accidentally dropped it a few times, and not a dent or scratch in sight! Plus, it's dishwasher safe, making cleanup a breeze after a long day.

            What truly sets this tumbler apart is its sleek and functional design. The slim profile fits perfectly in my hand and cup holder, while the double-wall vacuum insulation keeps my beverages hot or cold for hours on end. Whether I'm enjoying a piping hot cup of hot cocoa or a refreshing cold drink, the Stanley tumbler delivers every time.

            Overall, I highly recommend the Stanley 40oz Tumbler in Citron to anyone in search of a stylish, durable, and functional hydration solution. It's the perfect companion for any adventure, and its easy washability ensures that it will remain a staple in my daily routine for years to come.""",
            """
            OK, I'm sure you pros out there are doing just fine but after years of thermometering and instant thermometering and testing and tasting, I've not gotten out of all the cooking what I wanted or expected; I couldn't even get decent poached chicken no matter the methodology laid out (lol). But here comes an unexpected life saver: Combustion Predictive Thermometer & Display. Wow! Looked it up and it said it could be used for baked goods. Wife's pumpkin loaf was the first test and never has it turned out so good. Next, a bison steak (which I got in a swap deal); again, smashing success. Next, my nemesis: chicken breast. Yeah, you do it right and it's TENDER. The predicative feature is very successful doing its thing. There's an unexpected side bene: you get to see how what you're doing is going and, as a result, you may make changes to cooking times with more confidence. Last example: some top sirloin (hey, it was on sale): best ever nailing the desired doneness. of medium rare. The app is easy to use and the bluetooth worked fine but I was fairly close. Since there's cooking intelligence lacking at this end, some "thermometer AI" is more than welcome. P.S. be sure you read all material carefully, Be sure you understand the marker lines on the thermometer. The info is all there but it could probably do with an edit. Oh, and do the software upgrades.""",
            "I wanted to like this product but it just lost connection way too many times.",
            "Había leído comentarios de otro comprador que tuvo el mismo problema, en la publicación específica que es de 40 Oz y te llega uno de 30 oz, el producto es de buena calidad pero no es lo se específica en la publicación",
            """
            I get it. Everyone is buying these now after years of not caring about Stanley tumblers because of social media. The problem with viral crap like this is we get caught up in fitting in and jumping on the band wagon that we fail to see what's wrong with a product before buying it.
            THIS TUMBLER IS NOT LEAK PROOF. It's not even a little resistent to leaking. Even if you have the top fully closed and the straw taken out, the liquid inside will leak out like crazy if you tip it over even slightly. To me, if I'm going to carry around 30-40oz of hot or cold liquids then the tumbler MUST prevent said liquids from coming out. I understand it's not a water bottle, but that's a lame technicality that Stanley shouldn't cling to. At a MINIMUM the tumbler should be leak proof if I take out the straw and close the top. Furthermore, the sip top closing mechanism seems very flimsy and can be easily bended out of place, so beware of turning it too hard or especially dropping your tumbler.
            I am highly disappointed for being sucked into thinking this was a reliable tumbler that would replace others I have. Granted they are not as nice looking, but they do the job of holding AND containing the water I take with me all day to and from work in NYC.
            I do NOT recommend this tumbler and I would suggest that Stanley fix these important issues instead of focusing on more colors and patterns.""",
            """
            Newsflash: After all the hype surrounding this insulated tumbler, I finally decided to purchase one. Unfortunately, after two days, I happened upon an article on Google about these tumblers when I noticed one particular word that caught my attention in the title...Lead! I read the article in which the company admitted to using lead as part of the sealing agent that helps seal and insulate the tumbler. I was instantly mortified at what I just read and informed my mother about the article because she had purchase one right after me. I was shocked that Stanley would keep this a secret for so long after selling what I would think possibly several thousand of these insulated tumblers. Staying healthy is hard enough without a company feeding me a product with lead in it! The nerve and dare I say audacity of this company after so many incidents involving lead in this country affecting children especially. Needless to say I will be returning this item post haste and will not be purchasing another Stanley product anytime soon or maybe even ever!""");

    static final List<String> BOOK = List.of(
            """
            "Elon Musk" by Walter Isaacson is an extraordinary biographical exploration of one of the most fascinating and innovative figures of our time. As an admirer of Elon Musk and his ventures, I found this book to be an incredibly insightful and inspiring read that goes far beyond the typical biography.

            Walter Isaacson is renowned for his meticulous research and ability to provide a comprehensive account of his subjects. He delves deep into Musk's life, from his childhood in South Africa to his founding of multiple groundbreaking companies like SpaceX and Tesla. Isaacson's writing shines in its ability to humanize Musk, a man often seen as an enigmatic genius: the book covers his personal struggles, his successes, and his vulnerabilities.

            The book details Musk's relentless pursuit of innovation and his willingness to take risks that others deemed impossible. For aspiring entrepreneurs, it provides a treasure trove of lessons on perseverance and problem-solving. His ability to craft a compelling narrative makes this biography read more like an adventure novel.""",
            """
            I finally had the chance to read Walter Isaacson's latest book on Elon Musk over the holidays. This engaging read delves into Musk's innovative work - from space exploration and sleek electric car designs to satellite internet and AI advancements. The narrative provides an insight into Musk's thought process, highlighting his strategic thinking, learn-by-trying approach, and bold decision-making.

            The structure of the book, with short and readable chapters, enhances understanding and keeps you engaged. Isaacson's thorough research and extensive interviews unlock the deeper significance of Musk's projects, beyond just technology.

            While the book is comprehensive, I wish it had delved deeper into Elon Musk's insights on AI. Given his pivotal roles in OpenAI and xAI, readers would find the book even more valuable with a more extensive exploration of Musk's perspectives on AI.""",
            """
            An excellent biography of an exceptional person. This book gave insight into what has driven him. Like Steve Jobs, Musk is absolutely focused on the end product with minimal concern about the path. He doesn't fear taking risks along the way.
            It would be very hard to live with such a person and this seems fairly well documented. The purchase of Twitter/X is particularly interesting - things got complicated, and not helped by Musk's propensity to do stupid things (a recurring theme).
            Anyhow, reading this well written book provides insight to one of the most productive people of our time. I recommend it highly.""");

    private Reviews() {
    }

    static String asXml(List<String> reviews) {
        return IntStream.range(0, reviews.size())
            .mapToObj(i -> "<review index=\"%d\">\n%s\n</review>".formatted(i + 1, reviews.get(i)))
            .collect(Collectors.joining("\n", "<reviews>\n", "\n</reviews>"));
    }
}
