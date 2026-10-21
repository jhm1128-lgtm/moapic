package app.moapic.gallery;
import org.junit.Test;
import static org.junit.Assert.*;
public class TextSearchTest {
    @Test public void findsKoreanEnglishAndDigitsAcrossLines(){assertTrue(TextSearch.matches("예약 ABC\n2048", "예약 abc 2048"));assertFalse(TextSearch.matches("예약 ABC", "예약 2048"));}
    @Test public void findsFormattedNumbers(){assertTrue(TextSearch.matches("금액 12,900원\n010-1234-5678", "12900"));assertTrue(TextSearch.matches("010-1234-5678", "01012345678"));}
    @Test public void supportsEmptyAndNormalizedKorean(){assertTrue(TextSearch.matches(null,""));assertTrue(TextSearch.matches("가나다",java.text.Normalizer.normalize("가나다",java.text.Normalizer.Form.NFD)));}
}
