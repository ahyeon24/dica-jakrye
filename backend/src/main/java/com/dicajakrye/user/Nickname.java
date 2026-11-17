package com.dicajakrye.user;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 닉네임 규칙: 2~20자, 한글·영문·숫자·밑줄 (API.md 2장). 프로필 주소(/users/{닉네임})에 쓴다. */
public final class Nickname {

    private static final Pattern RULE = Pattern.compile("^[가-힣A-Za-z0-9_]{2,20}$");

    /** 운영자로 오해할 수 있는 이름. 대소문자 무시 */
    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "root", "system", "official",
            "관리자", "운영자", "운영진", "디카작례", "dicajakrye");

    private Nickname() {
    }

    public static boolean isValid(String nickname) {
        return nickname != null && RULE.matcher(nickname).matches();
    }

    public static boolean isReserved(String nickname) {
        return RESERVED.contains(nickname.toLowerCase(Locale.ROOT));
    }
}
