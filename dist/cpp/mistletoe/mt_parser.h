#ifndef MT_PARSER_H
#define MT_PARSER_H

#include "mt_expr.h"
#include <regex>
#include <string>
#include <unordered_map>
#include <vector>

namespace mistletoe
{
    struct _Parser
    {
        enum TT { NIL, OPEN, CLOSE, COMMA, DOT, HEX_NUMBER, NUMBER, STR_DOUBLE, STR_SINGLE, KEYWORD, IDENTIFIER };

        struct TTDetails
        {
            TT type;
            std::string label;
            std::regex pattern;
        };

        enum SyntaxMode { NORMAL, STRING_EMBEDDED };

        static const int ERROR_CONTEXT{15};

        const std::string input;
        std::string token;
        size_t pos;
        std::unordered_map<TT,TTDetails> tt_map;

        _Parser(std::string input);
        ptr<_Expr> parse_string_literal();
        ptr<_Expr> parse_list(SyntaxMode syntax_mode);
        bool is_whitespace(char ch) const;
        TT next(std::vector<TT> expected_tokens);
        MTException parse_err();
    };
}

#endif
