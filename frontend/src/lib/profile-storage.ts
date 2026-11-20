import type { UserProfile } from "./types";
import { apiFetch } from "./api";

export const PROFILE_STORAGE_KEY = "aidlink.profile.v1";

export function loadProfile(): UserProfile | null {
  const value = window.localStorage.getItem(PROFILE_STORAGE_KEY);
  if (!value) return null;
  try {
    return JSON.parse(value) as UserProfile;
  } catch {
    return null;
  }
}

export function saveProfile(profile: UserProfile): void {
  window.localStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(profile));
}

function hasProfileValue(profile: UserProfile): boolean {
  return Object.values(profile).some((value) => value !== null && value !== "");
}

export async function loadEffectiveProfile(): Promise<UserProfile | null> {
  const stored = loadProfile();
  try {
    const serverProfile = await apiFetch<UserProfile>("/api/v1/me/profile");
    if (!hasProfileValue(serverProfile)) return stored;
    saveProfile(serverProfile);
    return serverProfile;
  } catch {
    return stored;
  }
}

