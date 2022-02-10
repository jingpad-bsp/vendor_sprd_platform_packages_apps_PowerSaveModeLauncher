package com.sprd.powersavemodelauncher.allapps;

import com.sprd.powersavemodelauncher.BaseModelTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link DefaultAppSearchAlgorithm}
 */
@RunWith(RobolectricTestRunner.class)
public class DefaultAppSearchAlgorithmTest extends BaseModelTestCase {
    private DefaultAppSearchAlgorithm mAppSearch;

    @Before
    public void setUp() {
        super.setUp();
        mAppSearch = new DefaultAppSearchAlgorithm(null);
    }


    @Test
    public void testMatches() {
        assertTrue(mAppSearch.matches(getInfo("white cow"), new String[]{"cow"}));
        assertFalse(mAppSearch.matches(getInfo("whiteCow"), new String[]{"cow"}));
        assertFalse(mAppSearch.matches(getInfo("whiteCOW"), new String[]{"cow"}));
        assertFalse(mAppSearch.matches(getInfo("whitecowCOW"), new String[]{"cow"}));
        assertFalse(mAppSearch.matches(getInfo("white2cow"), new String[]{"cow"}));

        assertFalse(mAppSearch.matches(getInfo("whitecow"), new String[]{"cow"}));
        assertFalse(mAppSearch.matches(getInfo("whitEcow"), new String[]{"cow"}));

        assertFalse(mAppSearch.matches(getInfo("whitecowCow"), new String[]{"cow"}));
        assertTrue(mAppSearch.matches(getInfo("whitecow cow"), new String[]{"cow"}));
        assertFalse(mAppSearch.matches(getInfo("whitecowcow"), new String[]{"cow"}));
        assertFalse(mAppSearch.matches(getInfo("whit ecowcow"), new String[]{"cow"}));

        assertFalse(mAppSearch.matches(getInfo("cats&dogs"), new String[]{"dog"}));
        assertFalse(mAppSearch.matches(getInfo("cats&Dogs"), new String[]{"dog"}));
        assertFalse(mAppSearch.matches(getInfo("cats&Dogs"), new String[]{"&"}));

        assertFalse(mAppSearch.matches(getInfo("2+43"), new String[]{"43"}));
        assertFalse(mAppSearch.matches(getInfo("2+43"), new String[]{"3"}));

        assertTrue(mAppSearch.matches(getInfo("Q"), new String[]{"q"}));
        assertTrue(mAppSearch.matches(getInfo("  Q"), new String[]{"q"}));

        // match lower case words
        assertTrue(mAppSearch.matches(getInfo("elephant"), new String[]{"e"}));

        assertTrue(mAppSearch.matches(getInfo("电子邮件"), new String[]{"电"}));
        assertTrue(mAppSearch.matches(getInfo("电子邮件"), new String[]{"电子"}));
        assertFalse(mAppSearch.matches(getInfo("电子邮件"), new String[]{"子"}));
        assertFalse(mAppSearch.matches(getInfo("电子邮件"), new String[]{"邮件"}));

        assertFalse(mAppSearch.matches(getInfo("Bot"), new String[]{"ba"}));
        assertFalse(mAppSearch.matches(getInfo("bot"), new String[]{"ba"}));
    }

    @Test
    public void testMatchesVN() {
        assertTrue(mAppSearch.matches(getInfo("다운로드"), new String[]{"다"}));
        assertTrue(mAppSearch.matches(getInfo("드라이브"), new String[]{"드"}));
        assertFalse(mAppSearch.matches(getInfo("다운로드 드라이브"), new String[]{"ㄷ"}));
        assertFalse(mAppSearch.matches(getInfo("운로 드라이브"), new String[]{"ㄷ"}));
        assertFalse(mAppSearch.matches(getInfo("abc"), new String[]{"åbç"}));
        assertFalse(mAppSearch.matches(getInfo("Alpha"), new String[]{"ål"}));

        assertFalse(mAppSearch.matches(getInfo("다운로드 드라이브"), new String[]{"ㄷㄷ"}));
        assertFalse(mAppSearch.matches(getInfo("로드라이브"), new String[]{"ㄷ"}));
        assertFalse(mAppSearch.matches(getInfo("abc"), new String[]{"åç"}));
    }
}
