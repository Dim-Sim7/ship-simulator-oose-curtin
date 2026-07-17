#include "mt_parser.h"
#include "mt_execstate.h"
#include "mt_vals.h"
#include <sstream>

namespace mistletoe
{
    _Parser::_Parser(std::string input) : input(input), token(""), pos(0)
    {
        std::vector<TTDetails> tt_vec{
            TTDetails{TT::OPEN,       u8"\u201c(\u201d",  std::regex("^\\(")},
            TTDetails{TT::CLOSE,      u8"\u201c)\u201d",  std::regex("^\\)")},
            TTDetails{TT::COMMA,      u8"\u201c,\u201d",  std::regex("^,")},
            TTDetails{TT::DOT,        u8"\u201c.\u201d",  std::regex("^\\.")},
            TTDetails{TT::HEX_NUMBER, u8"<hex-number>",   std::regex("^[-+]?0x[0-9a-fA-F]+")},
            TTDetails{TT::NUMBER,     u8"<number>",       std::regex("^[-+]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]+)?")},
            TTDetails{TT::STR_DOUBLE, u8"\u201c\"\u201d", std::regex("^\"")},
            TTDetails{TT::STR_SINGLE, u8"\u201c'\u201d",  std::regex("^'")},
            TTDetails{TT::KEYWORD,    u8"<keyword>",      std::regex("^[A-Z][a-zA-Z0-9]*")},
            TTDetails{TT::IDENTIFIER, u8"<identifier>",   std::regex("^[a-z][a-zA-Z0-9_]*")}
        };

        for(TTDetails& tt_details : tt_vec)
        {
            tt_map.emplace(tt_details.type, tt_details);
        }
    }

    void add_last_str(std::vector<ptr<_Expr>>& components, std::stringstream* sb)
    {
        std::string last_str{sb->str()};
        if(last_str.size() > 0)
        {
            *sb = std::stringstream{};
            components.push_back(_single_expr("<str-component>", [=](ptr<_Scope> _s){ return _StrV::of(last_str); }));
        }
    }

    ptr<_Expr> _Parser::parse_string_literal()
    {
        const std::regex ESCAPE_REGEX("^[%DH(]|x[0-9a-fA-F]{2}|u[0-9a-fA-F]{4}|U[0-9a-fA-F]{6}");
        std::smatch escape_match;

        std::vector<ptr<_Expr>> components;
        std::stringstream sb;
        size_t input_len = input.size();
        size_t start_pos = pos;

        char delim_ch = input[pos];
        pos += 1;

        while(pos < input_len)
        {
            char ch = input[pos];
            if(ch == delim_ch)
            {
                pos += 1;
                add_last_str(components, &sb);
                return _single_expr("<str-literal>", [=](ptr<_Scope> scope)
                {
                    std::stringstream final_sb;
                    for(const ptr<_Expr>& c : components)
                    {
                        c->_eval(scope, [&](ptr<_Val> v) { final_sb << v->to_str(); });
                    }
                    return _StrV::of(final_sb.str());
                });
            }
            switch(ch)
            {
                case '\\':
                    switch(((pos + 1) < input_len) ? input[pos + 1] : '\0')
                    {
                        case 't':  sb.put('\t'); break;
                        case 'n':  sb.put('\n'); break;
                        case 'r':  sb.put('\r'); break;
                        case '"':  sb.put('"');  break;
                        case '\\': sb.put('\\'); break;
                        default:
                            throw parse_err() << "illegal \\-escape";
                    }
                    pos += 2;
                    break;

                case '%':
                {
                    if(!std::regex_search(input.cbegin() + pos + 1, input.cend(), escape_match, ESCAPE_REGEX))
                    {
                        throw parse_err() << u8"illegal %-escape (use \u201c%%\u201d for a literal \u201c%\u201d)";
                    }
                    auto escape = escape_match.str();
                    pos += 1 + escape.size();
                    switch(escape[0])
                    {
                        case '%':  sb.put('%'); break;
                        case '\'': sb.put('\''); break;
                        case 'D':  sb.put('$'); break;
                        case 'H':  sb.put('#'); break;
                        case 'x': case 'u': case 'U':
                            sb << _chr(std::stol(escape.substr(1), nullptr, 16));
                            break;
                        case '(':
                            pos -= 1;
                            add_last_str(components, &sb);
                            components.push_back(parse_list(SyntaxMode::STRING_EMBEDDED));
                            break;
                        default:
                            throw MTException() << "Internal error";
                    }
                    break;
                }

                case '"':
                    throw parse_err() << u8"illegal char \u201c\"\u201d (use \\\")";

                case '$': case '#':
                    throw parse_err() << u8"illegal char \u201c" << ch << u8"\u201d (use a %-escape)";

                default:
                    pos += 1;
                    sb << ch;
                    break;
            }
        }
        pos = start_pos;
        throw parse_err() << "unclosed string literal";
    }

    ptr<_Expr> _Parser::parse_list(SyntaxMode syntax_mode)
    {
        auto str_tt = (syntax_mode == SyntaxMode::NORMAL) ? TT::STR_DOUBLE : TT::STR_SINGLE;
        auto identifier_tt = (syntax_mode == SyntaxMode::NORMAL) ? TT::NIL : TT::IDENTIFIER;

        ptr<std::vector<ptr<_Expr>>> expr_list{new std::vector<ptr<_Expr>>{}};
        next({TT::OPEN});
        TT tt = next({TT::HEX_NUMBER, TT::NUMBER, str_tt, identifier_tt, TT::KEYWORD, TT::CLOSE});
        while(tt != TT::CLOSE)
        {
            switch(tt)
            {
                case TT::NUMBER:     expr_list->push_back(_arg_to_expr(std::stod(token))); break;
                case TT::HEX_NUMBER: expr_list->push_back(_arg_to_expr((uint64_t)std::stol(token, nullptr, 16))); break;
                case TT::STR_DOUBLE:
                case TT::STR_SINGLE:
                    pos -= token.size();
                    expr_list->push_back(parse_string_literal());
                    break;

                case TT::IDENTIFIER:
                case TT::KEYWORD:
                {
                    auto name = token;
                    auto jump_it = _Jump::instances.find(name);
                    if(jump_it != _Jump::instances.end())
                    {
                        expr_list->push_back(_arg_to_expr(*jump_it->second));
                    }
                    else
                    {
                        ptr<_ExprBase> expr;
                        _Operator* op;
                        _Operator::ExprType exprType;

                        if(tt == TT::IDENTIFIER)
                        {
                            op = nullptr;
                            exprType = _Operator::_VExprT;
                            expr = std::make_shared<_VExpr>(_VExpr(_single_expr("<identifier>",
                                                                                [=](ptr<_Scope> _s){ return _StrV::of(name); })));
                        }
                        else if((op = _Operator::lookup(_Operator::ExprType::Nil, name)))
                        {
                            exprType = op->get_return_type();
                            expr = op->call(nullptr, op->has_right_operand() ? parse_list(syntax_mode) : nullptr);
                        }
                        else
                        {
                            pos -= name.size();
                            throw parse_err() << u8"unexpected symbol \u201c" << name << u8"\u201d";
                        }

                        // Chained calls
                        while(next({TT::DOT, TT::COMMA, TT::CLOSE}) == TT::DOT)
                        {
                            next({TT::KEYWORD});

                            if((op = _Operator::lookup(exprType, token)))
                            {
                                exprType = op->get_return_type();
                                if(op->has_right_operand())
                                {
                                    expr = op->call(expr, parse_list(syntax_mode));
                                }
                                else
                                {
                                    expr = op->call(expr, nullptr);
                                    next({TT::OPEN});
                                    next({TT::CLOSE});
                                }
                            }
                            else
                            {
                                pos -= token.size();
                                throw parse_err() << u8"unexpected symbol \u201c" << token << u8"\u201d";
                            }
                        }
                        pos -= token.size();

                        if(!_Operator::type_is_full_expression(exprType))
                        {
                            throw parse_err() << "incomplete expression";
                        }
                        expr_list->push_back(std::static_pointer_cast<_Expr>(expr));
                    }
                    break;
                }

                default:
                    throw MTException() << "Internal error";
            }

            tt = next({TT::COMMA, TT::CLOSE});
            if(tt == TT::COMMA)
            {
                tt = next({TT::HEX_NUMBER, TT::NUMBER, str_tt, identifier_tt, TT::KEYWORD});
            }
        }
        return _expr_cat(expr_list);
    }

    bool _Parser::is_whitespace(char ch) const
    {
        return ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n';
    }

    _Parser::TT _Parser::next(std::vector<TT> expected_tokens)
    {
        size_t input_len = input.size();
        while(pos < input_len && is_whitespace(input[pos]))
        {
            pos++;
        }

        std::smatch token_match;
        for(TT& tt : expected_tokens)
        {
            if(tt == TT::NIL) { continue; }
            if(std::regex_search(input.cbegin() + pos, input.cend(), token_match, tt_map[tt].pattern))
            {
                token = token_match.str();
                pos += token.size();
                return tt;
            }
        }

        size_t n_expected = expected_tokens.size();
        if(n_expected == 1)
        {
            throw parse_err() << "expected " << tt_map[expected_tokens[0]].label;
        }

        std::stringstream expected_str;
        expected_str << tt_map[expected_tokens[0]].label;
        for(size_t i = 1; i < n_expected; i++)
        {
            if(expected_tokens[i] != TT::NIL)
            {
                expected_str << ", " << tt_map[expected_tokens[i]].label;
            }
        }
        throw parse_err() << "expected one of " << expected_str.str();
    }

    MTException _Parser::parse_err()
    {
        int64_t input_len = input.size();
        int64_t p = pos;
        std::string inp = input;
        if(inp[0] == '(' && inp[input_len - 1] == ')')
        {
            p -= 1;
            input_len -= 2;
            inp.erase(inp.size() - 1, 1);
            inp.erase(0, 1);
        }

        int64_t line = 1;
        int64_t col = 1;
        for(int64_t i = 0; i < p; i++)
        {
            if(inp[i] == '\n')
            {
                line++;
                col = 0;
            }
            col++;
        }

        int64_t pre_cutoff = (p <= ERROR_CONTEXT) ? 0 : p - ERROR_CONTEXT;
        int64_t post_cutoff = ((input_len - p) <= ERROR_CONTEXT) ? input_len : p + ERROR_CONTEXT;

        std::stringstream suffix;
        suffix << u8" in \u201c" << (pre_cutoff > 0 ? "..." : "") << inp.substr(pre_cutoff, p - pre_cutoff) << u8"\u25b6"
                                 << inp.substr(p, post_cutoff - p) << (post_cutoff < input_len ? "..." : "") << u8"\u201d";

        return MTException(suffix.str()) << "Parsing error at line " << line << ", col " << col << ": ";
    }
}
