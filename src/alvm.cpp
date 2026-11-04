#include <cstdint>
#include <fstream>
#include <iostream>
#include <map>
#include <stdexcept>
#include <string>
#include <vector>
struct Token { std::string s; int line, col; };
struct Ins { uint8_t op; int32_t arg=0; std::string text; };
enum { PUSH=1, LOAD, STORE, ADD, SUB, MUL, DIV, PRINT, HALT, PUSHSTR };
static std::vector<Token> lex(const std::string& s) {
 std::vector<Token> v; size_t i=0; int l=1,c=1;
 while(i<s.size()){ char x=s[i]; if(x==' '||x=='\r'||x=='\t'||x=='\n'){if(x=='\n'){l++;c=1;}else c++;i++;continue;}
 if(x=='/'&&i+1<s.size()&&s[i+1]=='/'){while(i<s.size()&&s[i]!='\n'){i++;c++;}continue;}
 int sl=l,sc=c; std::string t;
 if(isalpha((unsigned char)x)||x=='_'){while(i<s.size()&&(isalnum((unsigned char)s[i])||s[i]=='_')){t+=s[i++];c++;}}
 else if(isdigit((unsigned char)x)){while(i<s.size()&&isdigit((unsigned char)s[i])){t+=s[i++];c++;}}
 else if(x=='"'){i++;c++;while(i<s.size()&&s[i]!='"'){if(s[i]=='\\'&&i+1<s.size()){i++;c++;char e=s[i++];c++;t+=(e=='n'?'\n':e=='t'?'\t':e);}else{t+=s[i++];c++;}}if(i==s.size())throw std::runtime_error("Unterminated string at "+std::to_string(sl)+":"+std::to_string(sc));i++;c++;t="\x01"+t;}
 else {t=x;i++;c++;}
 v.push_back({t,sl,sc});
 } v.push_back({"<eof>",l,c}); return v;
}
class Parser {
 std::vector<Token> t; size_t p=0; std::vector<Ins> code; std::map<std::string,int> vars;
 std::string cur(){return t[p].s;} bool eat(const std::string& s){if(cur()==s){p++;return true;}return false;}
 void need(const std::string& s){if(!eat(s))throw std::runtime_error("Expected '"+s+"' at "+std::to_string(t[p].line)+":"+std::to_string(t[p].col));}
 void emit(int op,int arg=0,std::string text=""){code.push_back({(uint8_t)op,arg,text});}
 void expr(){term();while(cur()=="+"||cur()=="-"){auto op=cur();p++;term();emit(op=="+"?ADD:SUB);}}
 void term(){factor();while(cur()=="*"||cur()=="/"){auto op=cur();p++;factor();emit(op=="*"?MUL:DIV);}}
 void factor(){if(eat("-")){emit(PUSH,0);factor();emit(SUB);return;} if(cur().size()&&isdigit((unsigned char)cur()[0])){emit(PUSH,std::stoi(cur()));p++;return;} if(cur().size()&&cur()[0]=='\x01'){emit(PUSHSTR,0,cur().substr(1));p++;return;} if(eat("(")){expr();need(")");return;} if(isalpha((unsigned char)cur()[0])||cur()[0]=='_'){auto n=cur();p++;if(!vars.count(n))throw std::runtime_error("Unknown variable: "+n);emit(LOAD,vars[n]);return;}throw std::runtime_error("Expected expression at "+std::to_string(t[p].line)+":"+std::to_string(t[p].col));}
 void stmt(){if(cur()=="int"||cur()=="var"||cur()=="string"){p++;auto n=cur();if(n=="<eof>")throw std::runtime_error("Expected variable name");p++;if(vars.count(n))throw std::runtime_error("Duplicate variable: "+n);int slot=(int)vars.size();vars[n]=slot;need("=");expr();emit(STORE,slot);need(";");return;}
 if(eat("AL")){need(".");need("print");need("(");expr();need(")");need(";");emit(PRINT);return;}
 if(eat("return")){if(!eat(";")){expr();need(";");}emit(HALT);return;}
 auto n=cur();if(!vars.count(n))throw std::runtime_error("Unknown statement or variable: "+n);p++;need("=");expr();need(";");emit(STORE,vars[n]);}
public:
 explicit Parser(const std::string& s):t(lex(s)){}
 std::vector<Ins> parse(){if(!(eat("room")||eat("class")))throw std::runtime_error("Expected room declaration");if(cur()=="<eof>")throw std::runtime_error("Expected program name");p++;need("{");eat("public");eat("static");if(!(eat("void")||eat("int")))throw std::runtime_error("Expected main return type");need("main");need("(");need(")");need("{");while(cur()!="}"&&cur()!="<eof>")stmt();need("}");need("}");if(cur()!="<eof>")throw std::runtime_error("Unexpected trailing input");if(code.empty()||code.back().op!=HALT)emit(HALT);return code;}
};
static void w32(std::ofstream& f,uint32_t x){for(int i=0;i<4;i++)f.put((char)(x>>(i*8)));}
static uint32_t r32(std::ifstream& f){uint32_t x=0;for(int i=0;i<4;i++){int c=f.get();if(c<0)throw std::runtime_error("Truncated room file");x|=(uint32_t)(uint8_t)c<<(8*i);}return x;}
static void save(const std::string& p,const std::vector<Ins>& v){std::ofstream f(p,std::ios::binary);if(!f)throw std::runtime_error("Cannot write output");f.write("ALRM",4);w32(f,1);w32(f,v.size());for(auto&i:v){f.put(i.op);w32(f,(uint32_t)i.arg);w32(f,i.text.size());f.write(i.text.data(),i.text.size());}}
static std::vector<Ins> load(const std::string& p){std::ifstream f(p,std::ios::binary);char m[4];f.read(m,4);if(f.gcount()!=4||std::string(m,4)!="ALRM")throw std::runtime_error("Invalid room magic");if(r32(f)!=1)throw std::runtime_error("Unsupported room version");auto n=r32(f);if(n>100000)throw std::runtime_error("Instruction limit");std::vector<Ins> v;while(n--){int op=f.get();if(op<1||op>10)throw std::runtime_error("Invalid opcode");int arg=(int)r32(f);auto z=r32(f);if(z>1000000)throw std::runtime_error("Constant too large");std::string s(z,'\0');f.read(s.data(),z);if((uint32_t)f.gcount()!=z)throw std::runtime_error("Truncated constant");v.push_back({(uint8_t)op,arg,s});}if(f.peek()!=EOF)throw std::runtime_error("Trailing bytes");return v;}
static void verify(const std::vector<Ins>& v){if(v.empty()||v.back().op!=HALT)throw std::runtime_error("Missing HALT");int d=0;for(auto&i:v){if(i.op==PUSH||i.op==PUSHSTR||i.op==LOAD)d++;else if(i.op==STORE||i.op==PRINT){if(--d<0)throw std::runtime_error("Stack underflow");}else if(i.op==ADD||i.op==SUB||i.op==MUL||i.op==DIV){if(d<2)throw std::runtime_error("Stack underflow");d--;}}}
static void run(const std::vector<Ins>& v){verify(v);std::vector<std::string>s;std::map<int,std::string> slots;auto pop=[&](){if(s.empty())throw std::runtime_error("Runtime stack underflow");auto x=s.back();s.pop_back();return x;};for(auto&i:v)switch(i.op){case PUSH:s.push_back(std::to_string(i.arg));break;case PUSHSTR:s.push_back(i.text);break;case LOAD:if(!slots.count(i.arg))throw std::runtime_error("Uninitialized variable");s.push_back(slots[i.arg]);break;case STORE:slots[i.arg]=pop();break;case PRINT:std::cout<<pop()<<'\n';break;case ADD:case SUB:case MUL:case DIV:{int b=std::stoi(pop()),a=std::stoi(pop());if(i.op==DIV&&b==0)throw std::runtime_error("Division by zero");s.push_back(std::to_string(i.op==ADD?a+b:i.op==SUB?a-b:i.op==MUL?a*b:a/b));break;}case HALT:return;default:throw std::runtime_error("Bad opcode");}}
int main(int argc,char**argv){try{if(argc<3){std::cerr<<"Usage: alvm compile file.al [-o file.room] | run file.room | disasm file.room\n";return 2;}std::string cmd=argv[1],in=argv[2];if(cmd=="compile"){std::ifstream f(in);if(!f)throw std::runtime_error("Cannot open source");std::string src((std::istreambuf_iterator<char>(f)),{});std::string out=in.substr(0,in.find_last_of('.'))+".room";for(int i=3;i+1<argc;i++)if(std::string(argv[i])=="-o")out=argv[i+1];auto v=Parser(src).parse();save(out,v);std::cout<<"Compiled "<<out<<" ("<<v.size()<<" instructions)\n";}else if(cmd=="run")run(load(in));else if(cmd=="disasm"){auto v=load(in);verify(v);for(size_t i=0;i<v.size();i++)std::cout<<i<<": opcode "<<(int)v[i].op<<" operand "<<v[i].arg<<" "<<v[i].text<<"\n";}else throw std::runtime_error("Unknown command");return 0;}catch(const std::exception&e){std::cerr<<"AL error: "<<e.what()<<'\n';return 1;}}
