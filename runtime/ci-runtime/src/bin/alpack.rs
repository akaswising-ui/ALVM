use ci_runtime::{create_al_package, verify_al_package};
use std::env;
use std::path::{Path, PathBuf};
use std::process::ExitCode;

fn usage() {
    eprintln!(
        "AL application packager\n\nUsage:\n  alpack create <output.AL> <app-name> <major.minor.patch> <main.room> [asset-file ...]\n  alpack verify <application.AL>"
    );
}

fn main() -> ExitCode {
    let args: Vec<String> = env::args().collect();
    match args.get(1).map(String::as_str) {
        Some("create") if args.len() >= 6 => {
            let output = Path::new(&args[2]);
            let name = &args[3];
            let version = &args[4];
            let room = Path::new(&args[5]);
            let assets: Vec<PathBuf> = args[6..].iter().map(PathBuf::from).collect();
            match create_al_package(output, name, version, room, &assets) {
                Ok(()) => {
                    println!("Created {} ({name} {version})", output.display());
                    ExitCode::SUCCESS
                }
                Err(error) => {
                    eprintln!("{error}");
                    ExitCode::FAILURE
                }
            }
        }
        Some("verify") if args.len() == 3 => {
            match verify_al_package(Path::new(&args[2])) {
                Ok(result) => {
                    println!("{result}");
                    ExitCode::SUCCESS
                }
                Err(error) => {
                    eprintln!("{error}");
                    ExitCode::FAILURE
                }
            }
        }
        _ => {
            usage();
            ExitCode::from(2)
        }
    }
}
