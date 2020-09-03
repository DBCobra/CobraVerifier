extern crate chrono;
extern crate serde;
extern crate serde_json;
extern crate serde_yaml;
#[macro_use]
extern crate serde_derive;

use std::fmt;
use std::path::Path;
use std::fs;
use std::fs::File;
use std::io::{Read, BufWriter};
use std::env;
use std::assert;

use std::collections::HashSet;

use chrono::{DateTime, Duration, Local};


// ========history data structure=========
// copied from dbcop

#[derive(Serialize, Deserialize, Eq, PartialEq, Clone)]
pub struct Event {
    pub write: bool,
    pub variable: usize,
    pub value: usize,
    pub success: bool,
}

#[derive(Serialize, Deserialize, Eq, PartialEq, Clone)]
pub struct Transaction {
    pub events: Vec<Event>,
    pub success: bool,
}

pub type Session = Vec<Transaction>;

impl fmt::Debug for Event {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        let repr = format!(
            "<{}({}):{:2}>",
            if self.write { 'W' } else { 'R' },
            self.variable,
            self.value
        );
        if !self.success {
            write!(f, "!")?;
        }
        write!(f, "{}", repr)
    }
}

impl Event {
    pub fn read(var: usize) -> Self {
        Event {
            write: false,
            variable: var,
            value: 0,
            success: false,
        }
    }
    pub fn write(var: usize, val: usize) -> Self {
        Event {
            write: true,
            variable: var,
            value: val,
            success: false,
        }
    }
}

impl fmt::Debug for Transaction {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        let repr = format!("{:?}", self.events);
        if !self.success {
            write!(f, "!")?;
        }
        write!(f, "{}", repr)
    }
}

#[derive(Clone, Debug, Default, Deserialize, Serialize)]
pub struct HistParams {
    id: usize,
    n_node: usize,
    n_variable: usize,
    n_transaction: usize,
    n_event: usize,
}

impl HistParams {
    pub fn get_id(&self) -> usize {
        self.id
    }
    pub fn set_id(&mut self, id: usize) {
        self.id = id;
    }
    pub fn get_n_node(&self) -> usize {
        self.n_node
    }
    pub fn get_n_variable(&self) -> usize {
        self.n_variable
    }
    pub fn get_n_transaction(&self) -> usize {
        self.n_transaction
    }
    pub fn get_event(&self) -> usize {
        self.n_event
    }
}

#[derive(Deserialize, Serialize, Debug)]
pub struct History {
    params: HistParams,
    info: String,
    start: DateTime<Local>,
    end: DateTime<Local>,
    data: Vec<Session>,
}

impl History {
    pub fn new(
        params: HistParams,
        info: String,
        start: DateTime<Local>,
        end: DateTime<Local>,
        data: Vec<Session>,
    ) -> Self {
        History {
            params,
            info,
            start,
            end,
            data,
        }
    }

    pub fn get_id(&self) -> usize {
        self.params.get_id()
    }

    pub fn get_data(&self) -> &Vec<Session> {
        &self.data
    }

    pub fn get_cloned_data(&self) -> Vec<Session> {
        self.data.clone()
    }

    pub fn get_params(&self) -> &HistParams {
        &self.params
    }

    pub fn get_cloned_params(&self) -> HistParams {
        self.params.clone()
    }

    pub fn get_duration(&self) -> Duration {
        self.end - self.start
    }
}


// ========read from Cobra logs=========

fn bytes2long(src : &[u8]) -> u64 {
    let mut value : u64 = 0;
    for i in 0..8 {
        value = (value << 8) + ( (src[i] as u64) & 0xff);
    }
    return value;
}

fn parse_log(buf : Vec<u8>) -> Session {
    // (startTx, txnId) : 9B <br>
    // (commitTx, txnId) : 9B <br>
    // (write, writeId, key, val): 25B <br>
    // (read, write_TxnId, writeId, key, value) : 33B <br>
    let mut sess = Session::new();
    if buf.len() == 0 {
        return sess;
    }

    let mut op_type : char = 'X';
    let mut i : usize = 0;

    let mut cur_txn = Transaction {
        events: Vec::<Event>::new(),
        success: false,
    };

    loop {
        op_type = buf[i] as char;
        i = i + 1;

        match op_type {
            'S' => {
                let txnid = bytes2long(&buf[i .. i+8]);
                i = i + 8;
                // start a new transaction
                assert!(cur_txn.success == false);
                cur_txn = Transaction {
                    events: Vec::<Event>::new(),
                    success: true,
                }
            },
            'C' => {
                let txnid = bytes2long(&buf[i .. i+8]);
                i = i + 8;
                // end a new transaction; add to session
                assert!(cur_txn.success == true);
                sess.push(cur_txn);
                // set cur_txn to empty
                cur_txn = Transaction { events: Vec::<Event>::new(), success: false,};
            },
            'W' => {
                let wid = bytes2long(&buf[i .. i+8]);
                i = i + 8;
                let key = bytes2long(&buf[i .. i+8]);
                i = i + 8;
                let val = bytes2long(&buf[i .. i+8]);
                i = i + 8;
                // add write to current transaction
                assert!(cur_txn.success == true);
                let event = Event {
                    write: true,
                    variable: key as usize,
                    value: wid as usize, // FIXME: we should use "wid" as value because it is unique
                    success: true,
                };
                cur_txn.events.push(event);
            },
            'R' => {
                let w_txnid = bytes2long(&buf[i .. i+8]);
                i = i + 8;
                let w_wid = bytes2long(&buf[i .. i+8]);
                i = i + 8;
                let key = bytes2long(&buf[i .. i+8]);
                i = i + 8;
                let val = bytes2long(&buf[i .. i+8]);
                i = i + 8;
                // add write to current transaction
                assert!(cur_txn.success == true);
                let event = Event {
                    write: false,
                    variable: key as usize,
                    value: w_wid as usize, // FIXME:
                    success: true,
                };
                cur_txn.events.push(event);
            },
            _ => println!("ERROR! should not be here"),
        }

        if i >= buf.len() {
            break;
        }
    }

    return sess;
}


fn get_one_session(log_path : String) -> Session {
    let mut file = File::open(&log_path).unwrap();
    // let mut buffer = String::new();
    // file.read_to_string(&mut buffer);

    let mut buffer = Vec::new();
    file.read_to_end(&mut buffer);

    //println!("{} size = {}", log_path, buffer.len());
    //println!("{}" , buffer[0] as char);


    return parse_log(buffer);
}


// =========main logic======

fn main(){
    let args: Vec<String> = env::args().collect();

    if args.len() != 3 {
        println!("Usage: translator <Cobra-log-folder> <BE19-log-folder>");
        return;
    }
    let str_src = &args[1];
    let str_dst = &args[2];

    // load from Cobra logs
    let src = Path::new(&str_src);


    let start_time = Local::now();
    let mut sessions_w_fence : Vec<Session> = Vec::new();

    for entry in fs::read_dir(src).unwrap() {
        //println!("Name: {}", entry.unwrap().path().display())

        let f_path = entry.unwrap().path().display().to_string();

        // only care ".log" files
        if f_path.ends_with(".log") {
            sessions_w_fence.push(get_one_session(f_path));
        }
    }
    let end_time = Local::now();

    // there are fence txns; rm them from the session
    let mut n_fence_txns : usize = 0;
    let n_events_per_txn : usize = sessions_w_fence[0][0].events.len(); // should be the same for all txns

    let mut sessions = Vec::<Session>::new(); // new sessions without fence
    for sess_w_f in sessions_w_fence {
        let mut sess = Session::new();
        for txn in sess_w_f {
            if txn.events.len() != n_events_per_txn {
                // here is fence
                assert!(txn.events.len() == 1 || txn.events.len()==2);
                n_fence_txns = n_fence_txns + 1;
            } else {
                // here is normal
                sess.push(txn);
            }
        }
        sessions.push(sess);
    }



    // count meta-data
    let mut vars = HashSet::new();
    let mut n_txns : usize = 0;

    for sess in &sessions {
        n_txns = n_txns + sess.len(); // sess is a vector of txns
        for txn in sess {
            assert!(txn.events.len() == n_events_per_txn);
            for e in &txn.events {
                vars.insert(e.variable);
            }
        }
    }

    // create one init txn which writes all variables to "value"/"wid" 0xbebeebee
    // FIXME: do I need to make this txn with fixed size?
    let init_wid : usize = 0xbebeebee;
    let mut init_txn = Transaction { events: Vec::<Event>::new(), success: true,};
    for &var in &vars {
        init_txn.events.push(Event{
            write: true,
            variable: var as usize,
            value: init_wid, // FIXME: we should use "wid" as value because it is unique
            success: true,
        });
    }
    // insert it to the head of one session
    sessions[0].insert(0, init_txn);

    let h = History {
            params: HistParams {
                id: 0,
                n_node: sessions.len(), // number of nodes per history
                n_variable: vars.len(),  // number of variables per history
                n_transaction: n_txns, // number of transactions per history
                n_event: n_events_per_txn, // number of events per txn
            },
            info: "cobra logs".to_string(),
            start: start_time,
            end: end_time,
            data: sessions,
        };

    //println!("{:?}",h);

    // write to /tmp/BE19-logs
    let dst = Path::new(&str_dst);
    if !dst.is_dir() {
        fs::create_dir_all(dst).expect("failed to create directory");
    }

    let file = File::create(dst.join(format!("history.bincode")))
        .expect("couldn't create bincode file"); let buf_writer = BufWriter::new(file);
    bincode::serialize_into(buf_writer, &h)
        .expect("dumping history to bincode file went wrong");
}
