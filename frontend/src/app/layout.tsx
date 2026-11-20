import type { Metadata } from "next";
import { Navigation } from "../components/Navigation";
import { ServiceFooter } from "../components/ServiceFooter";
import "./globals.css";

export const metadata: Metadata = {
  title: "AIDLINK | 나에게 맞는 지원정책",
  description: "개인 조건에 맞는 공공 지원정책을 분석하고 상황 변화를 시뮬레이션합니다.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko" data-scroll-behavior="smooth">
      <body>
        <Navigation />
        {children}
        <ServiceFooter />
      </body>
    </html>
  );
}

