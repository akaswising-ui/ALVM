#include <algorithm>
#include <cctype>
#include <cstdint>
#include <fstream>
#include <iostream>
#include <limits>
#include <map>
#include <memory>
#include <queue>
#include <stdexcept>
#include <string>
#include <utility>
#include <variant>
#include <vector>

namespace al {
struct Error : std::runtime_error { using std::runtime_error::runtime_error; };
struct Token { std::string text; int line=1, col=1; };

static std::vector<Token> lex(const std::string& src) {
  std::vector<Token> out; size_t i=0; int line=1,col=1;
  auto advance=[&](){ char c=src[i++]; if(c=='\n'){++line;col=1;}else ++col; return c; };
  while(i<src.size()) {
    char c=src[i];
    if(std::isspace(static_cast<unsigned char>(c))){advance();continue;}
    if(c=='/' && i+1<src.size() && src[i+1]=='/'){while(i<src.size()&&src[i]!='\n')advance();continue;}
    if(c=='/' && i+1<src.size() && src[i+1]=='*'){int sl=line,sc=col;advance();advance();bool closed=false;while(i<src.size()){if(src[i]=='*'&&i+1<src.size()&&src[i+1]=='/'){advance();advance();closed=true;break;}advance();}if(!closed)throw Error("Unterminated block comment at "+std::to_string(sl)+":"+std::to_string(sc));continue;}
    int sl=line,sc=col; std::string t;
    if(std::isalpha(static_cast<unsigned char>(c))||c=='_'){while(i<src.size()&&(std::isalnum(static_cast<unsigned char>(src[i]))||src[i]=='_'))t+=advance();}
    else if(std::isdigit(static_cast<unsigned char>(c))){while(i<src.size()&&std::isdigit(static_cast<unsigned char>(src[i])))t+=advance();}
    else if(c=='"'){advance();bool closed=false;while(i<src.size()){char x=advance();if(x=='"'){closed=true;break;}if(x=='\\'){if(i>=src.size())break;char e=advance();switch(e){case 'n':t+='\n';break;case 't':t+='\t';break;case 'r':t+='\r';break;case '"':t+='"';break;case '\\':t+='\\';break;default:throw Error("Unknown string escape at "+std::to_string(line)+":"+std::to_string(col));}}else t+=x;}if(!closed)throw Error("Unterminated string at "+std::to_string(sl)+":"+std::to_string(sc));t="\x01"+t;}
    else { static const std::vector<std::string> two={"==","!=","<=",">=","&&","||"}; bool matched=false;for(const auto& op:two){if(src.compare(i,op.size(),op)==0){advance();advance();t=op;matched=true;break;}}if(!matched){if(std::string("{}();=+-*/%!<>.").find(c)==std::string::npos)throw Error("Unexpected character at "+std::to_string(sl)+":"+std::to_string(sc));t+=advance();}}
    out.push_back({t,sl,sc});
  }
  out.push_back({"<eof>",line,col});return out;
}

enum class Type { Int, String, Bool, Void, Unknown };
static std::string typeName(Type t){switch(t){case Type::Int:return"int";case Type::String:return"string";case Type::Bool:return"boolean";case Type::Void:return"void";default:return"unknown";}}
struct Expr {
  enum Kind { Integer, String, Boolean, Variable, Unary, Binary } kind;
  Token token; int64_t number=0; bool boolean=false; std::string text; std::unique_ptr<Expr> left,right; Type inferred=Type::Unknown;
};
struct Stmt {
  enum Kind { Block, Declare, Assign, Print, Return, If, While, For } kind;
  Token token; std::string name; Type declared=Type::Unknown;
  std::unique_ptr<Expr> expr, condition, incrementExpr;
  std::unique_ptr<Stmt> first, second, third;
  std::vector<std::unique_ptr<Stmt>> children;
};
struct Program { std::string name; std::unique_ptr<Stmt> body; };

class Parser {
  std::vector<Token> t; size_t p=0;
  const Token& tok()const{return t[p];} std::string cur()const{return tok().text;}
  bool eat(const std::string& s){if(cur()==s){++p;return true;}return false;}
  Token take(){return t[p++];}
  void need(const std::string& s){if(!eat(s))throw Error("Expected '"+s+"' at "+std::to_string(tok().line)+":"+std::to_string(tok().col));}
  std::unique_ptr<Expr> node(Expr::Kind k,Token tk){auto x=std::make_unique<Expr>();x->kind=k;x->token=std::move(tk);return x;}
  std::unique_ptr<Stmt> stmtNode(Stmt::Kind k,Token tk){auto x=std::make_unique<Stmt>();x->kind=k;x->token=std::move(tk);return x;}
  Type parseType(){if(eat("int"))return Type::Int;if(eat("string"))return Type::String;if(eat("boolean"))return Type::Bool;if(eat("void"))return Type::Void;throw Error("Unknown type at "+std::to_string(tok().line)+":"+std::to_string(tok().col));}
  std::unique_ptr<Expr> primary(){Token tk=tok();if(cur().size()&&std::isdigit(static_cast<unsigned char>(cur()[0]))){take();auto x=node(Expr::Integer,tk);try{x->number=std::stoll(tk.text);}catch(...){throw Error("Integer literal out of range at "+std::to_string(tk.line)+":"+std::to_string(tk.col));}return x;}if(cur().size()&&cur()[0]=='\x01'){take();auto x=node(Expr::String,tk);x->text=tk.text.substr(1);return x;}if(eat("true")||eat("false")){auto x=node(Expr::Boolean,tk);x->boolean=tk.text=="true";return x;}if(eat("(")){auto x=expression();need(")");return x;}if(std::isalpha(static_cast<unsigned char>(cur()[0]))||cur()[0]=='_'){take();auto x=node(Expr::Variable,tk);x->text=tk.text;return x;}throw Error("Expected expression at "+std::to_string(tok().line)+":"+std::to_string(tok().col));}
  std::unique_ptr<Expr> unary(){if(cur()=="!"||cur()=="-"){Token tk=take();auto x=node(Expr::Unary,tk);x->text=tk.text;x->left=unary();return x;}return primary();}
  template<class Next> std::unique_ptr<Expr> binary(Next next,const std::vector<std::string>& ops){auto x=(this->*next)();while(std::find(ops.begin(),ops.end(),cur())!=ops.end()){Token tk=take();auto n=node(Expr::Binary,tk);n->text=tk.text;n->left=std::move(x);n->right=(this->*next)();x=std::move(n);}return x;}
  std::unique_ptr<Expr> factor(){return binary(&Parser::unary,{"*","/","%"});}
  std::unique_ptr<Expr> term(){return binary(&Parser::factor,{"+","-"});}
  std::unique_ptr<Expr> compare(){return binary(&Parser::term,{"<",">","<=",">="});}
  std::unique_ptr<Expr> equality(){return binary(&Parser::compare,{"==","!="});}
  std::unique_ptr<Expr> logicalAnd(){return binary(&Parser::equality,{"&&"});}
  std::unique_ptr<Expr> logicalOr(){return binary(&Parser::logicalAnd,{"||"});}
  std::unique_ptr<Expr> expression(){return logicalOr();}
  bool isType(){return cur()=="int"||cur()=="string"||cur()=="boolean"||cur()=="var";}
  std::unique_ptr<Stmt> block(){Token tk=tok();need("{");auto b=stmtNode(Stmt::Block,tk);while(cur()!="}"&&cur()!="<eof>")b->children.push_back(statement());need("}");return b;}
  std::unique_ptr<Stmt> declaration(bool semicolon=true){Token tk=tok();Type ty=Type::Unknown;if(eat("var"))ty=Type::Unknown;else ty=parseType();auto s=stmtNode(Stmt::Declare,tk);s->declared=ty;s->name=take().text;if(s->name=="<eof>")throw Error("Expected variable name");need("=");s->expr=expression();if(semicolon)need(";");return s;}
  std::unique_ptr<Stmt> assignment(bool semicolon=true){Token tk=tok();auto s=stmtNode(Stmt::Assign,tk);s->name=take().text;need("=");s->expr=expression();if(semicolon)need(";");return s;}
  std::unique_ptr<Stmt> statement(){Token tk=tok();if(cur()=="{")return block();if(isType())return declaration();if(eat("AL")){auto s=stmtNode(Stmt::Print,tk);need(".");need("print");need("(");s->expr=expression();need(")");need(";");return s;}if(eat("return")){auto s=stmtNode(Stmt::Return,tk);if(!eat(";")){s->expr=expression();need(";");}return s;}if(eat("if")){auto s=stmtNode(Stmt::If,tk);need("(");s->condition=expression();need(")");s->first=block();if(eat("else"))s->second=block();return s;}if(eat("while")){auto s=stmtNode(Stmt::While,tk);need("(");s->condition=expression();need(")");s->first=block();return s;}if(eat("for")){auto s=stmtNode(Stmt::For,tk);need("(");if(isType())s->first=declaration(false);else s->first=assignment(false);need(";");s->condition=expression();need(";");s->second=assignment(false);need(")");s->third=block();return s;}if(std::isalpha(static_cast<unsigned char>(cur()[0]))||cur()[0]=='_')return assignment();throw Error("Unsupported statement at "+std::to_string(tok().line)+":"+std::to_string(tok().col));}
public:
  explicit Parser(const std::string& src):t(lex(src)){}
  Program parse(){if(!(eat("room")||eat("class")))throw Error("Program must start with room");Program out;out.name=take().text;need("{");eat("public");eat("static");if(!(eat("void")||eat("int")))throw Error("Expected main return type");need("main");need("(");need(")");out.body=block();need("}");if(cur()!="<eof>")throw Error("Unexpected token after program end: "+cur());return out;}
};

class SemanticAnalyzer {
  std::vector<std::map<std::string,Type>> scopes;
  void push(){scopes.emplace_back();}void pop(){scopes.pop_back();}
  Type lookup(const std::string& n){for(auto i=scopes.rbegin();i!=scopes.rend();++i){auto f=i->find(n);if(f!=i->end())return f->second;}throw Error("Unknown variable: "+n);}
  void declare(const std::string& n,Type t){auto& s=scopes.back();if(s.count(n))throw Error("Duplicate variable in scope: "+n);s[n]=t;}
  Type checkExpr(Expr& e){switch(e.kind){case Expr::Integer:e.inferred=Type::Int;break;case Expr::String:e.inferred=Type::String;break;case Expr::Boolean:e.inferred=Type::Bool;break;case Expr::Variable:e.inferred=lookup(e.text);break;case Expr::Unary:{Type a=checkExpr(*e.left);if(e.text=="-"&&a!=Type::Int)throw Error("Unary '-' requires int");if(e.text=="!"&&a!=Type::Bool)throw Error("Unary '!' requires boolean");e.inferred=e.text=="-"?Type::Int:Type::Bool;break;}case Expr::Binary:{Type a=checkExpr(*e.left),b=checkExpr(*e.right);const auto& op=e.text;if(op=="+"&&a==Type::String&&b==Type::String)e.inferred=Type::String;else if(op=="+"||op=="-"||op=="*"||op=="/"||op=="%"){if(a!=Type::Int||b!=Type::Int)throw Error("Operator '"+op+"' requires int operands");e.inferred=Type::Int;}else if(op=="<"||op==">"||op=="<="||op==">="){if(a!=Type::Int||b!=Type::Int)throw Error("Comparison '"+op+"' requires int operands");e.inferred=Type::Bool;}else if(op=="=="||op=="!="){if(a!=b)throw Error("Equality operands must have the same type");e.inferred=Type::Bool;}else if(op=="&&"||op=="||"){if(a!=Type::Bool||b!=Type::Bool)throw Error("Logical operator requires boolean operands");e.inferred=Type::Bool;}else throw Error("Unsupported operator: "+op);break;}}return e.inferred;}
  void checkStmt(Stmt& s){switch(s.kind){case Stmt::Block:push();for(auto& c:s.children)checkStmt(*c);pop();break;case Stmt::Declare:{Type rhs=checkExpr(*s.expr);Type actual=s.declared==Type::Unknown?rhs:s.declared;if(actual!=rhs)throw Error("Cannot assign "+typeName(rhs)+" to "+typeName(actual)+" variable '"+s.name+"'");declare(s.name,actual);break;}case Stmt::Assign:{Type lhs=lookup(s.name),rhs=checkExpr(*s.expr);if(lhs!=rhs)throw Error("Cannot assign "+typeName(rhs)+" to "+typeName(lhs)+" variable '"+s.name+"'");break;}case Stmt::Print:checkExpr(*s.expr);break;case Stmt::Return:if(s.expr)throw Error("main is void and cannot return a value");break;case Stmt::If:if(checkExpr(*s.condition)!=Type::Bool)throw Error("if condition must be boolean");checkStmt(*s.first);if(s.second)checkStmt(*s.second);break;case Stmt::While:if(checkExpr(*s.condition)!=Type::Bool)throw Error("while condition must be boolean");checkStmt(*s.first);break;case Stmt::For:push();checkStmt(*s.first);if(checkExpr(*s.condition)!=Type::Bool)throw Error("for condition must be boolean");checkStmt(*s.second);checkStmt(*s.third);pop();break;}}
public:
  void analyze(Program& p){(void)p;push();checkStmt(*p.body);pop();}
};

enum class Op : uint8_t { PushInt=1,PushString,PushBool,Load,Store,Add,Sub,Mul,Div,Mod,Neg,Not,Eq,Ne,Lt,Gt,Le,Ge,And,Or,Jump,JumpFalse,Print,Pop,Halt };
struct IR { Op op; int64_t arg=0; std::string text; };
class IRGenerator {
  std::vector<IR> code; std::map<std::string,int> slots; int next=0;
  int emit(Op op,int64_t arg=0,std::string text={}){code.push_back({op,arg,std::move(text)});return (int)code.size()-1;}
  void expression(const Expr& e){switch(e.kind){case Expr::Integer:emit(Op::PushInt,e.number);break;case Expr::String:emit(Op::PushString,0,e.text);break;case Expr::Boolean:emit(Op::PushBool,e.boolean?1:0);break;case Expr::Variable:emit(Op::Load,slots.at(e.text));break;case Expr::Unary:expression(*e.left);emit(e.text=="-"?Op::Neg:Op::Not);break;case Expr::Binary:{if(e.text=="&&"){expression(*e.left);int jf=emit(Op::JumpFalse,-1);expression(*e.right);int j=emit(Op::Jump,-1);code[jf].arg=(int64_t)code.size();emit(Op::PushBool,0);code[j].arg=(int64_t)code.size();break;}if(e.text=="||"){expression(*e.left);int jf=emit(Op::JumpFalse,-1);emit(Op::PushBool,1);int j=emit(Op::Jump,-1);code[jf].arg=(int64_t)code.size();expression(*e.right);code[j].arg=(int64_t)code.size();break;}expression(*e.left);expression(*e.right);if(e.text=="+")emit(Op::Add);else if(e.text=="-")emit(Op::Sub);else if(e.text=="*")emit(Op::Mul);else if(e.text=="/")emit(Op::Div);else if(e.text=="%")emit(Op::Mod);else if(e.text=="==")emit(Op::Eq);else if(e.text=="!=")emit(Op::Ne);else if(e.text=="<")emit(Op::Lt);else if(e.text==">")emit(Op::Gt);else if(e.text=="<=")emit(Op::Le);else if(e.text==">=")emit(Op::Ge);break;}}}
  void statement(const Stmt& s){switch(s.kind){case Stmt::Block:{auto saved=slots;for(const auto& c:s.children)statement(*c);slots=std::move(saved);break;}case Stmt::Declare:{int slot=next++;slots[s.name]=slot;expression(*s.expr);emit(Op::Store,slot);break;}case Stmt::Assign:expression(*s.expr);emit(Op::Store,slots.at(s.name));break;case Stmt::Print:expression(*s.expr);emit(Op::Print);break;case Stmt::Return:emit(Op::Halt);break;case Stmt::If:{expression(*s.condition);int jf=emit(Op::JumpFalse,-1);statement(*s.first);if(s.second){int j=emit(Op::Jump,-1);code[jf].arg=(int64_t)code.size();statement(*s.second);code[j].arg=(int64_t)code.size();}else code[jf].arg=(int64_t)code.size();break;}case Stmt::While:{int start=(int)code.size();expression(*s.condition);int jf=emit(Op::JumpFalse,-1);statement(*s.first);emit(Op::Jump,start);code[jf].arg=(int64_t)code.size();break;}case Stmt::For:{auto saved=slots;statement(*s.first);int start=(int)code.size();expression(*s.condition);int jf=emit(Op::JumpFalse,-1);statement(*s.third);statement(*s.second);emit(Op::Jump,start);code[jf].arg=(int64_t)code.size();slots=std::move(saved);break;}}}
public:
  std::vector<IR> generate(const Program& p){statement(*p.body);if(code.empty()||code.back().op!=Op::Halt)emit(Op::Halt);return code;}
};
struct Bytecode { Op op; int64_t arg=0; std::string text; };
class BytecodeLowerer { public: std::vector<Bytecode> lower(const std::vector<IR>& ir){std::vector<Bytecode> out;out.reserve(ir.size());for(const auto& i:ir)out.push_back({i.op,i.arg,i.text});return out;} };
static void writeU32(std::ofstream& f,uint32_t x){for(int i=0;i<4;++i)f.put((char)(x>>(8*i)));}
static uint32_t readU32(std::ifstream& f){uint32_t x=0;for(int i=0;i<4;++i){int c=f.get();if(c==EOF)throw Error("Truncated .room file");x|=(uint32_t)(uint8_t)c<<(8*i);}return x;}
static void writeU64(std::ofstream& f,uint64_t x){for(int i=0;i<8;++i)f.put((char)(x>>(8*i)));}
static uint64_t readU64(std::ifstream& f){uint64_t x=0;for(int i=0;i<8;++i){int c=f.get();if(c==EOF)throw Error("Truncated .room file");x|=(uint64_t)(uint8_t)c<<(8*i);}return x;}
static void saveRoom(const std::string& path,const std::vector<Bytecode>& code){std::ofstream f(path,std::ios::binary);if(!f)throw Error("Cannot create output file");f.write("ALRM",4);writeU32(f,2);writeU32(f,(uint32_t)code.size());for(const auto& i:code){f.put((char)i.op);writeU64(f,(uint64_t)i.arg);if(i.text.size()>1000000)throw Error("String constant too large");writeU32(f,(uint32_t)i.text.size());f.write(i.text.data(),(std::streamsize)i.text.size());}if(!f)throw Error("Failed writing .room file");}
static std::vector<Bytecode> loadRoom(const std::string& path){std::ifstream f(path,std::ios::binary);if(!f)throw Error("Cannot open .room file");char magic[4];f.read(magic,4);if(f.gcount()!=4||std::string(magic,4)!="ALRM")throw Error("Invalid .room magic");if(readU32(f)!=2)throw Error("Unsupported .room version (expected 2)");uint32_t n=readU32(f);if(n==0||n>1000000)throw Error("Invalid instruction count");std::vector<Bytecode> code;code.reserve(n);for(uint32_t k=0;k<n;++k){int op=f.get();if(op<1||op>(int)Op::Halt)throw Error("Invalid opcode");int64_t arg=(int64_t)readU64(f);uint32_t len=readU32(f);if(len>1000000)throw Error("Constant length limit exceeded");std::string text(len,'\0');if(len){f.read(text.data(),len);if((uint32_t)f.gcount()!=len)throw Error("Truncated string constant");}code.push_back({(Op)op,arg,std::move(text)});}if(f.peek()!=EOF)throw Error("Unexpected trailing bytes in .room file");return code;}
static int stackDelta(Op op){switch(op){case Op::PushInt:case Op::PushString:case Op::PushBool:case Op::Load:return 1;case Op::Store:case Op::JumpFalse:case Op::Print:case Op::Pop:return -1;case Op::Add:case Op::Sub:case Op::Mul:case Op::Div:case Op::Mod:case Op::Eq:case Op::Ne:case Op::Lt:case Op::Gt:case Op::Le:case Op::Ge:case Op::And:case Op::Or:return -1;default:return 0;}}
static void verify(const std::vector<Bytecode>& c){if(c.empty()||c.back().op!=Op::Halt)throw Error("Bytecode must end with HALT");std::vector<int> depth(c.size(),-1);std::queue<size_t> q;depth[0]=0;q.push(0);while(!q.empty()){size_t pc=q.front();q.pop();const auto& i=c[pc];int before=depth[pc];int need=0;switch(i.op){case Op::Store:case Op::JumpFalse:case Op::Print:case Op::Pop:need=1;break;case Op::Add:case Op::Sub:case Op::Mul:case Op::Div:case Op::Mod:case Op::Eq:case Op::Ne:case Op::Lt:case Op::Gt:case Op::Le:case Op::Ge:case Op::And:case Op::Or:need=2;break;default:break;}if(before<need)throw Error("Bytecode stack underflow at instruction "+std::to_string(pc));int after=before+stackDelta(i.op);auto visit=[&](int64_t dest){if(dest<0||(uint64_t)dest>=c.size())throw Error("Invalid jump target");size_t d=(size_t)dest;if(depth[d]==-1){depth[d]=after;q.push(d);}else if(depth[d]!=after)throw Error("Inconsistent stack depth at control-flow join");};if(i.op==Op::Jump)visit(i.arg);else if(i.op==Op::JumpFalse){visit(i.arg);if(pc+1<c.size())visit((int64_t)pc+1);}else if(i.op!=Op::Halt){if(pc+1>=c.size())throw Error("Execution can fall off end of bytecode");visit((int64_t)pc+1);}}}
using Value=std::variant<int64_t,std::string,bool>;
static std::string show(const Value& v){if(auto x=std::get_if<int64_t>(&v))return std::to_string(*x);if(auto x=std::get_if<std::string>(&v))return *x;return std::get<bool>(v)?"true":"false";}
static int64_t integer(const Value& v){if(auto x=std::get_if<int64_t>(&v))return *x;throw Error("Runtime type error: expected int");}
static bool boolean(const Value& v){if(auto x=std::get_if<bool>(&v))return *x;throw Error("Runtime type error: expected boolean");}
static int64_t checkedAdd(int64_t a,int64_t b){int64_t r;if(__builtin_add_overflow(a,b,&r))throw Error("Integer overflow");return r;}
static int64_t checkedSub(int64_t a,int64_t b){int64_t r;if(__builtin_sub_overflow(a,b,&r))throw Error("Integer overflow");return r;}
static int64_t checkedMul(int64_t a,int64_t b){int64_t r;if(__builtin_mul_overflow(a,b,&r))throw Error("Integer overflow");return r;}
static void execute(const std::vector<Bytecode>& c){verify(c);std::vector<Value> stack;std::map<int64_t,Value> locals;auto pop=[&](){if(stack.empty())throw Error("Runtime stack underflow");Value v=std::move(stack.back());stack.pop_back();return v;};size_t pc=0;while(pc<c.size()){const auto& i=c[pc];switch(i.op){case Op::PushInt:stack.emplace_back(i.arg);break;case Op::PushString:stack.emplace_back(i.text);break;case Op::PushBool:stack.emplace_back(i.arg!=0);break;case Op::Load:{auto f=locals.find(i.arg);if(f==locals.end())throw Error("Read of uninitialized local variable");stack.push_back(f->second);break;}case Op::Store:locals[i.arg]=pop();break;case Op::Print:std::cout<<show(pop())<<'\n';break;case Op::Pop:pop();break;case Op::Neg:{auto a=integer(pop());if(a==std::numeric_limits<int64_t>::min())throw Error("Integer overflow");stack.emplace_back(-a);break;}case Op::Not:stack.emplace_back(!boolean(pop()));break;case Op::Add:{Value b=pop(),a=pop();if(std::holds_alternative<std::string>(a)&&std::holds_alternative<std::string>(b))stack.emplace_back(std::get<std::string>(a)+std::get<std::string>(b));else stack.emplace_back(checkedAdd(integer(a),integer(b)));break;}case Op::Sub:{auto b=integer(pop()),a=integer(pop());stack.emplace_back(checkedSub(a,b));break;}case Op::Mul:{auto b=integer(pop()),a=integer(pop());stack.emplace_back(checkedMul(a,b));break;}case Op::Div:case Op::Mod:{auto b=integer(pop()),a=integer(pop());if(b==0)throw Error("Division by zero");if(a==std::numeric_limits<int64_t>::min()&&b==-1)throw Error("Integer overflow");stack.emplace_back(i.op==Op::Div?a/b:a%b);break;}case Op::Eq:case Op::Ne:{Value b=pop(),a=pop();bool same=a.index()==b.index()&&a==b;stack.emplace_back(i.op==Op::Eq?same:!same);break;}case Op::Lt:case Op::Gt:case Op::Le:case Op::Ge:{auto b=integer(pop()),a=integer(pop());bool r=i.op==Op::Lt?a<b:i.op==Op::Gt?a>b:i.op==Op::Le?a<=b:a>=b;stack.emplace_back(r);break;}case Op::And:case Op::Or:{bool b=boolean(pop()),a=boolean(pop());stack.emplace_back(i.op==Op::And?(a&&b):(a||b));break;}case Op::Jump:pc=(size_t)i.arg;continue;case Op::JumpFalse:{bool cond=boolean(pop());if(!cond){pc=(size_t)i.arg;continue;}break;}case Op::Halt:return;}++pc;}}
} // namespace al

int main(int argc,char** argv){try{if(argc<3){std::cerr<<"Usage: alvm compile <file.al> [-o file.room] | run <file.room> | disasm <file.room>\n";return 2;}std::string command=argv[1],input=argv[2];if(command=="compile"){std::ifstream f(input);if(!f)throw al::Error("Cannot open source file");std::string source((std::istreambuf_iterator<char>(f)),{});al::Program program=al::Parser(source).parse();al::SemanticAnalyzer().analyze(program);auto ir=al::IRGenerator().generate(program);auto bc=al::BytecodeLowerer().lower(ir);std::string output=input;auto dot=output.find_last_of('.');if(dot!=std::string::npos)output.resize(dot);output+=".room";for(int i=3;i+1<argc;++i)if(std::string(argv[i])=="-o")output=argv[i+1];al::saveRoom(output,bc);std::cout<<"Compiled "<<output<<" ("<<bc.size()<<" instructions)\n";}else if(command=="run"){al::execute(al::loadRoom(input));}else if(command=="disasm"){auto bc=al::loadRoom(input);al::verify(bc);for(size_t i=0;i<bc.size();++i)std::cout<<i<<": op="<<(int)bc[i].op<<" arg="<<bc[i].arg<<(bc[i].text.empty()?"":" text=\""+bc[i].text+"\"")<<'\n';}else throw al::Error("Unknown command: "+command);return 0;}catch(const std::exception& e){std::cerr<<"AL error: "<<e.what()<<'\n';return 1;}}
