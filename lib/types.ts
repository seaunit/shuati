export type Role = "ADMIN" | "USER";
export type Status = "ENABLED" | "DISABLED";
export type QuestionType = "SINGLE" | "MULTI" | "SHORT";
export type Difficulty = "EASY" | "MEDIUM" | "HARD";
export type Verdict = "CORRECT" | "PARTIAL" | "WRONG" | "PENDING";
export type JudgeSource = "LOCAL" | "AI" | "SELF";

export interface Profile {
  id: string;
  email: string;
  nickname: string | null;
  role: Role;
  status: Status;
  created_at: string;
  last_login_at: string | null;
}

export interface Bank {
  id: number;
  name: string;
  description: string | null;
  is_default: boolean;
  owner_id: string | null;
  created_at: string;
  is_public?: boolean;
  unit_count?: number;
  question_count?: number;
  answered_count?: number;
}

export interface Unit {
  id: number;
  bank_id: number;
  name: string;
  sort: number;
  created_at: string;
  question_count?: number;
  answered_count?: number;
}

export interface Option {
  key: string;
  text: string;
}

export interface Question {
  id: number;
  unit_id: number;
  type: QuestionType;
  content: string;
  options: Option[] | null;
  answer: string;
  key_points: string[] | null;
  difficulty: Difficulty;
  explanation: string | null;
  images: string[] | null;
  tags: string[] | null;
  source_url: string | null;
  status: "ON" | "OFF";
  created_at: string;
  updated_at: string;
}

export interface PracticeRecord {
  id: number;
  user_id: string;
  question_id: number;
  user_answer: string;
  judge_source: JudgeSource;
  verdict: Verdict;
  score: number | null;
  ai_feedback: Record<string, unknown> | null;
  duration_ms: number | null;
  created_at: string;
}

export interface ImportTask {
  id: string;
  user_id: string;
  kind: "doc" | "text" | "url";
  status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
  progress: number;
  total_chunks: number;
  done_chunks: number;
  bank_name: string;
  bank_description: string | null;
  items: unknown[] | null;
  error: string | null;
  created_at: string;
  updated_at: string;
}